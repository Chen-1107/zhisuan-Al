// molopt.cpp - 智算铝宾 分子优化计算核心 (Win32 控制台，零外部依赖)
// 移植 mol_optimizer.py 中不适合 Python 的 CPU 密集部件：
//   ForceField(键/角/扭转/范德华/静电能量)、ConstraintEngine(约束检查)、
//   MDPMolecularOptimization(6动作环境)、QLearningTrainer(3层MLP+手动反向传播)
// 数值公式与 Python 版逐一对应（含 get_dihedrals 的原实现行为）。
//
// 输入文件格式（行式，UTF-8）：
//   NATOMS n
//   <symbol> <q> <Z> <x> <y> <z>        （n 行）
//   NBONDS m
//   <i> <j> <order>                      （m 行，0 基）
//   REFBOND <i> <j> <r0_pm>              （可选，实验参考键长，覆盖共价半径求和）
//   ERRBOND <i> <j> <r_err_pm> <w>       （可选，错误集键长惩罚：高斯型 w·exp(-((r-r_err)/10pm)²)）
//   EPISODES e                           （0 = 仅评估能量模式）
//   STEPS s
//   SEED sd                              （可选，缺省随机）
//
// 输出：JSON 写到 stdout（坐标等数值用 %.17g 保证精度往返）
// 编译：cl /O2 /EHsc /utf-8 /DUNICODE /D_UNICODE molopt.cpp

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cstdint>
#include <cctype>
#include <array>
#include <random>
#include <string>
#include <vector>
#include <map>
#include <algorithm>
#include <limits>

// 数值常量（与 mol_optimizer.py 保持一致，保证公式等价）
static const double NATGRAD_EPS = 1e-8;   // 自然梯度对角Fisher分母平滑项
static const double ENT_PSEL = 1e-12;     // 路径熵对数保护项

// ==================== 元素数据 ====================
struct Elem {
    double vdw_pm;      // 范德华半径 pm（能量用）
    double cov_pm;     // 共价半径 pm（能量/键约束用）
    double vdw_ang;    // 范德华半径 Å（约束用）
    int valency;
    int lone_pairs;
};

static const std::map<std::string, Elem> ELEM = {
    {"C", {170, 77, 1.70, 4, 0}}, {"H", {120, 31, 1.20, 1, 0}},
    {"O", {152, 73, 1.52, 2, 2}}, {"N", {155, 71, 1.55, 3, 1}},
    {"F", {147, 64, 1.47, 1, 3}}, {"Cl", {175, 99, 1.75, 1, 3}},
    {"Br", {185, 114, 1.85, 1, 3}}, {"I", {198, 133, 1.98, 1, 3}},
    {"S", {180, 104, 1.80, 2, 2}}, {"P", {180, 107, 1.80, 3, 1}},
    {"B", {180, 87, 1.80, 3, 0}}, {"Si", {210, 111, 2.10, 4, 0}},
    {"Al", {184, 121, 1.84, 3, 0}},
    {"Se", {190, 120, 1.90, 2, 2}}, {"Te", {206, 138, 2.06, 2, 2}},
    {"Cu", {140, 132, 1.40, 4, 0}},
};
static const Elem ELEM_DEF = {170, 77, 1.70, 4, 0};

static Elem elemOf(const std::string& s) {
    auto it = ELEM.find(s);
    return it != ELEM.end() ? it->second : ELEM_DEF;
}

// ==================== 数据结构 ====================
struct AtomIn {
    std::string symbol;
    double q;
    int Z;
    double x, y, z;
    // 派生（静态）
    Elem e;
    double total_bond = 0;
    int n_H = 0;
    int hyb = 2; // 0=sp 1=sp2 2=sp3
};

struct Mol {
    std::vector<AtomIn> atoms;
    std::vector<std::array<int, 2>> bondIJ;
    std::vector<double> bondOrder;
    std::vector<std::vector<int>> neighbors;
    // 预计算几何拓扑表
    std::vector<std::array<int, 3>> angles;    // (i,j,k) 顶点在中间
    std::vector<std::array<int, 4>> dihedrals; // (i,j,k,l)
    std::vector<std::array<int, 2>> nonbonded; // 非键原子对 i<j
    // CIF 实验参考键长：key = (long long)min(i,j)<<32 | max(i,j)，值 r0(pm)
    std::map<long long, double> refR0;
    // 错误集键长（二重态错误建模特征）：key 同上，值 (r_err_pm, w)。惩罚高斯型：
    // E_err = Σ w·exp(-((r-r_err)/σ)²)，σ=10pm —— 结构收敛到错误键长附近时施罚
    std::map<long long, std::pair<double, double>> errR0;

    static long long refKey(int i, int j) {
        int a = i < j ? i : j, b = i < j ? j : i;
        return ((long long)a << 32) | (unsigned int)b;
    }
    // 键 b 的平衡长度（pm）：实验参考值优先，否则共价半径求和+键级修正
    double bondR0pm(size_t b) const {
        auto it = refR0.find(refKey(bondIJ[b][0], bondIJ[b][1]));
        if (it != refR0.end()) return it->second;
        return atoms[bondIJ[b][0]].e.cov_pm + atoms[bondIJ[b][1]].e.cov_pm
               + 0.1 * (1.0 - bondOrder[b]);
    }

    void buildTopology() {
        int n = (int)atoms.size();
        neighbors.assign(n, {});
        for (size_t b = 0; b < bondIJ.size(); b++) {
            neighbors[bondIJ[b][0]].push_back(bondIJ[b][1]);
            neighbors[bondIJ[b][1]].push_back(bondIJ[b][0]);
            atoms[bondIJ[b][0]].total_bond += bondOrder[b];
            atoms[bondIJ[b][1]].total_bond += bondOrder[b];
        }
        for (auto& a : atoms) {
            // |q|：形式电荷（正负皆然）消耗成键容量——[Se-] 1键0H；过渡金属（Cu 等）
            // 配体由输入显式给出，不加隐式氢。旧公式 -q 会给负离子加幻影氢。
            bool isTM = (a.symbol == "Cu");
            a.n_H = isTM ? 0
                : std::max(0, (int)std::lround(a.e.valency - a.total_bond - std::fabs(a.q)));
            int steric = (int)std::lround(a.total_bond + a.e.lone_pairs);
            a.hyb = (steric == 2) ? 0 : (steric == 3) ? 1 : 2; // else 分支均归 sp3（同 Python）
        }
        // 键角表：对每个 ≥2 邻居的中心原子取邻居两两组合（保持 Python 顺序）
        for (int j = 0; j < n; j++) {
            auto& ne = neighbors[j];
            for (size_t a = 0; a < ne.size(); a++)
                for (size_t b = a + 1; b < ne.size(); b++)
                    angles.push_back({ne[a], j, ne[b]});
        }
        // 二面角表：忠实复刻 Python 实现——内层空循环使 i 停留在 j 的最后一个邻居
        for (int j = 0; j < n; j++) {
            for (int k : neighbors[j]) {
                if (k <= j) continue;
                int i = neighbors[j].back(); // Python 循环变量泄漏的等价行为
                for (int l : neighbors[k]) {
                    if (l == j) continue;
                    dihedrals.push_back({i, j, k, l});
                }
            }
        }
        // 非键对（能量与 vdW 约束共用）
        // 化学力场惯例：1,2(成键)与1,3(键角约束已涵盖)都不属于 vdW 作用范围，
        // 只对 1,4 及以上原子对施加范德华作用。故排除直接成键对与共享共同邻居的对
        // （与 Python ForceField/ConstraintEngine 的 _share_neighbor 判据一致）。
        std::vector<std::vector<char>> bonded(n, std::vector<char>(n, 0));
        for (auto& b : bondIJ) { bonded[b[0]][b[1]] = 1; bonded[b[1]][b[0]] = 1; }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (bonded[i][j]) continue;
                bool shared13 = false;
                for (int v : neighbors[i])
                    if (bonded[v][j]) { shared13 = true; break; }
                if (!shared13) nonbonded.push_back({i, j});
            }
        }
    }
};

typedef std::array<double, 3> P3;
static inline double dist(const P3& a, const P3& b) {
    double dx = a[0]-b[0], dy = a[1]-b[1], dz = a[2]-b[2];
    return std::sqrt(dx*dx + dy*dy + dz*dz);
}

// ==================== 力场（公式与 Python 一致） ====================
struct Forces {
    const Mol* mol;
    double k_b = 1.0, k_theta = 0.1, epsilon = 0.1;

    double bondEnergy(const std::vector<P3>& pos) const {
        double E = 0;
        for (size_t b = 0; b < mol->bondIJ.size(); b++) {
            int i = mol->bondIJ[b][0], j = mol->bondIJ[b][1];
            double r = dist(pos[i], pos[j]) * 100.0; // Å -> pm
            double r0 = mol->bondR0pm(b);
            double d = r - r0;
            E += 0.5 * k_b * d * d;
        }
        return E;
    }

    // 错误集键长惩罚（与 Python ForceField.error_penalty 公式一致，σ=10pm）
    double errPenalty(const std::vector<P3>& pos) const {
        if (mol->errR0.empty()) return 0.0;
        double E = 0;
        for (auto& kv : mol->errR0) {
            int i = (int)(kv.first >> 32);
            int j = (int)(kv.first & 0xFFFFFFFFu);
            double r = dist(pos[i], pos[j]) * 100.0; // Å -> pm
            double d = (r - kv.second.first) / 10.0;
            E += kv.second.second * std::exp(-d * d);
        }
        return E;
    }

    static double theta0RadOf(int hyb) {
        static const double t0[3] = {180.0, 120.0, 109.5}; // sp sp2 sp3
        return t0[hyb] * 3.14159265358979323846 / 180.0;
    }

    double angleOf(const std::vector<P3>& pos, int i, int j, int k) const {
        P3 v1 = {pos[i][0]-pos[j][0], pos[i][1]-pos[j][1], pos[i][2]-pos[j][2]};
        P3 v2 = {pos[k][0]-pos[j][0], pos[k][1]-pos[j][1], pos[k][2]-pos[j][2]};
        double n1 = std::sqrt(v1[0]*v1[0]+v1[1]*v1[1]+v1[2]*v1[2]);
        double n2 = std::sqrt(v2[0]*v2[0]+v2[1]*v2[1]+v2[2]*v2[2]);
        if (n1 == 0 || n2 == 0) return 0.0;
        double cosv = (v1[0]*v2[0]+v1[1]*v2[1]+v1[2]*v2[2]) / (n1*n2);
        cosv = std::max(-1.0, std::min(1.0, cosv));
        return std::acos(cosv);
    }

    double angleEnergy(const std::vector<P3>& pos) const {
        double E = 0;
        for (auto& a : mol->angles) {
            double theta = angleOf(pos, a[0], a[1], a[2]);
            double t0 = theta0RadOf(mol->atoms[a[1]].hyb);
            double d = theta - t0;
            E += 0.5 * k_theta * d * d;
        }
        return E;
    }

    double dihedralOf(const std::vector<P3>& pos, int i, int j, int k, int l) const {
        P3 v1 = {pos[i][0]-pos[j][0], pos[i][1]-pos[j][1], pos[i][2]-pos[j][2]};
        P3 v2 = {pos[k][0]-pos[j][0], pos[k][1]-pos[j][1], pos[k][2]-pos[j][2]};
        P3 v3 = {pos[l][0]-pos[k][0], pos[l][1]-pos[k][1], pos[l][2]-pos[k][2]};
        auto cross = [](const P3& a, const P3& b) -> P3 {
            return {a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]};
        };
        auto dot = [](const P3& a, const P3& b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; };
        auto norm = [](const P3& a) { return std::sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]); };
        P3 n1 = cross(v1, v2), n2 = cross(v2, v3);
        double nn1 = norm(n1), nn2 = norm(n2);
        if (nn1 == 0 || nn2 == 0) return 0.0;
        n1 = {n1[0]/nn1, n1[1]/nn1, n1[2]/nn1};
        n2 = {n2[0]/nn2, n2[1]/nn2, n2[2]/nn2};
        double nv2 = norm(v2);
        P3 v2h = {v2[0]/nv2, v2[1]/nv2, v2[2]/nv2};
        return std::atan2(dot(cross(n1, v2h), n2), dot(n1, n2));
    }

    double torsionEnergy(const std::vector<P3>& pos) const {
        double E = 0;
        for (auto& d : mol->dihedrals) {
            double phi = dihedralOf(pos, d[0], d[1], d[2], d[3]);
            E += 1.0 * (1.0 + std::cos(3.0 * phi));
        }
        return E;
    }

    double vdwEnergy(const std::vector<P3>& pos) const {
        double E = 0;
        for (auto& p : mol->nonbonded) {
            double r = dist(pos[p[0]], pos[p[1]]);
            if (r < 0.001) { E += 1e10; continue; }
            double rmin = (mol->atoms[p[0]].e.vdw_pm + mol->atoms[p[1]].e.vdw_pm) / 100.0;
            double sr = rmin / r;
            E += 4.0 * epsilon * (std::pow(sr, 12) - std::pow(sr, 6));
        }
        return E;
    }

    double electrostaticEnergy(const std::vector<P3>& pos) const {
        double E = 0;
        for (auto& p : mol->nonbonded) {
            double r = dist(pos[p[0]], pos[p[1]]);
            if (r < 0.001) { E += 1e10; continue; }
            E += mol->atoms[p[0]].q * mol->atoms[p[1]].q / r;
        }
        return E;
    }

    double total(const std::vector<P3>& pos) const {
        return bondEnergy(pos) + angleEnergy(pos) + torsionEnergy(pos)
             + vdwEnergy(pos) + electrostaticEnergy(pos);
    }
};

// ==================== 约束引擎 ====================
struct Constraints {
    const Mol* mol;
    double bond_tol = 0.3, angle_tol = 30.0, vdw_factor = 0.7;
    int valency_tol = 1;

    struct BondV { int i, j; double dev; };
    struct AngV { int i, j, k; double dev; };
    struct VdwV { int i, j; double overlap; };
    struct ValV { int i; double excess; };

    std::vector<BondV> bondViol(const std::vector<P3>& pos) const {
        std::vector<BondV> v;
        for (size_t b = 0; b < mol->bondIJ.size(); b++) {
            int i = mol->bondIJ[b][0], j = mol->bondIJ[b][1];
            double r = dist(pos[i], pos[j]) * 100.0;
            double r0 = mol->bondR0pm(b);
            double rel = std::fabs(r - r0) / r0;
            if (rel > bond_tol) v.push_back({i, j, rel});
        }
        return v;
    }

    std::vector<AngV> angleViol(const std::vector<P3>& pos, const Forces& ff) const {
        std::vector<AngV> v;
        static const double t0deg[3] = {180.0, 120.0, 109.5};
        for (auto& a : mol->angles) {
            double theta = ff.angleOf(pos, a[0], a[1], a[2]) * 180.0 / 3.14159265358979323846;
            double t0 = t0deg[mol->atoms[a[1]].hyb];
            double dev = std::fabs(theta - t0);
            if (dev > angle_tol) v.push_back({a[0], a[1], a[2], dev});
        }
        return v;
    }

    std::vector<VdwV> vdwViol(const std::vector<P3>& pos) const {
        std::vector<VdwV> v;
        for (auto& p : mol->nonbonded) {
            double r = dist(pos[p[0]], pos[p[1]]);
            double rmin = mol->atoms[p[0]].e.vdw_ang + mol->atoms[p[1]].e.vdw_ang;
            if (r < vdw_factor * rmin) v.push_back({p[0], p[1], rmin - r});
        }
        return v;
    }

    std::vector<ValV> valencyViol() const {
        std::vector<ValV> v;
        for (size_t i = 0; i < mol->atoms.size(); i++) {
            double val = mol->atoms[i].total_bond + mol->atoms[i].n_H;
            double maxv = mol->atoms[i].e.valency + valency_tol;
            if (val > maxv) v.push_back({(int)i, val - maxv});
        }
        return v;
    }

    double energy(const std::vector<P3>& pos, const Forces& ff) const {
        double E = 0;
        for (auto& b : bondViol(pos)) E += 100.0 * b.dev * b.dev;
        for (auto& a : angleViol(pos, ff)) E += 50.0 * (a.dev / 10.0) * (a.dev / 10.0);
        for (auto& w : vdwViol(pos)) E += 200.0 * (w.overlap / 10.0) * (w.overlap / 10.0);
        for (auto& t : valencyViol()) E += 1000.0 * t.excess * t.excess;
        return E;
    }

    int totalViol(const std::vector<P3>& pos, const Forces& ff) const {
        return (int)(bondViol(pos).size() + angleViol(pos, ff).size()
                   + vdwViol(pos).size() + valencyViol().size());
    }

    bool isValid(const std::vector<P3>& pos, const Forces& ff) const {
        return energy(pos, ff) < 1.0 && totalViol(pos, ff) == 0;
    }
};

// ==================== Q 网络（3 层 MLP + 手动反向传播） ====================
struct QNet {
    int sd, hd, ad;
    std::vector<double> W1, b1, W2, b2, W3, b3; // W1: sd×hd  W2: hd×hd  W3: hd×ad
    // 自然梯度对角 Fisher 累加器（acc += g^2，与 mol_optimizer.py 一致）
    std::vector<double> accW1, accb1, accW2, accb2, accW3, accb3;

    QNet(int stateDim, int hidden, int actionDim, std::mt19937_64& rng)
        : sd(stateDim), hd(hidden), ad(actionDim) {
        W1.resize(sd * hd); b1.resize(hd);
        W2.resize(hd * hd); b2.resize(hd);
        W3.resize(hd * ad); b3.resize(ad);
        accW1.assign(W1.size(), 0.0); accb1.assign(b1.size(), 0.0);
        accW2.assign(W2.size(), 0.0); accb2.assign(b2.size(), 0.0);
        accW3.assign(W3.size(), 0.0); accb3.assign(b3.size(), 0.0);
        std::normal_distribution<double> n1(0, std::sqrt(2.0 / sd));
        std::normal_distribution<double> n2(0, std::sqrt(2.0 / hd));
        for (auto& w : W1) w = n1(rng);
        for (auto& w : W2) w = n2(rng);
        for (auto& w : W3) w = n2(rng);
    }

    // forward 同时保留隐藏层激活（update 复用）
    void forward(const std::vector<double>& x,
                 std::vector<double>& h1, std::vector<double>& h2,
                 std::vector<double>& q) const {
        h1.assign(hd, 0.0);
        for (int h = 0; h < hd; h++) {
            double s = b1[h];
            for (int i = 0; i < sd; i++) s += x[i] * W1[(size_t)i * hd + h];
            h1[h] = s > 0 ? s : 0;
        }
        h2.assign(hd, 0.0);
        for (int h = 0; h < hd; h++) {
            double s = b2[h];
            for (int i = 0; i < hd; i++) s += h1[i] * W2[(size_t)i * hd + h];
            h2[h] = s > 0 ? s : 0;
        }
        q.assign(ad, 0.0);
        for (int a = 0; a < ad; a++) {
            double s = b3[a];
            for (int i = 0; i < hd; i++) s += h2[i] * W3[(size_t)i * ad + a];
            q[a] = s;
        }
    }

    std::vector<double> predict(const std::vector<double>& x) const {
        std::vector<double> h1, h2, q;
        forward(x, h1, h2, q);
        return q;
    }

    // Q 值温度 softmax 策略概率（路径系综软策略，与 Python softmax_probs 一致）
    std::vector<double> softmax_probs(const std::vector<double>& x, double tau) const {
        auto qf = predict(x);
        for (auto& v : qf) v /= tau;
        double mx = *std::max_element(qf.begin(), qf.end());
        double sum = 0;
        for (auto& v : qf) { v = std::exp(v - mx); sum += v; }
        for (auto& v : qf) v /= sum;
        return qf;
    }

    // 路径熵 H[π_θ]：softmax 策略的香农熵，抑制过早坍缩到单一动作
    double q_entropy(const std::vector<double>& x, double tau) const {
        auto p = softmax_probs(x, tau);
        double h = 0;
        for (auto& v : p) h -= v * std::log(v + ENT_PSEL);
        return h;
    }

    // 自然梯度更新：acc += g^2（对角 Fisher），步长缩放 1/sqrt(acc+eps)。
    // 各层梯度用更新前的权重一次算齐后统一应用（与 Python 语义一致）。
    void update(const std::vector<double>& x, int action, double target, double lr) {
        std::vector<double> h1, h2, q;
        forward(x, h1, h2, q);
        double dq = 2.0 * (q[action] - target);
        std::vector<double> dW3(hd * ad, 0.0), db3(ad, 0.0);
        std::vector<double> dh2(hd, 0.0), dW2(hd * hd, 0.0), db2(hd, 0.0);
        std::vector<double> dh1(hd, 0.0), dW1(sd * hd, 0.0), db1(hd, 0.0);
        for (int h = 0; h < hd; h++) {
            dW3[(size_t)h * ad + action] = dq * h2[h];
            if (h2[h] > 0) dh2[h] = dq * W3[(size_t)h * ad + action];
        }
        db3[action] = dq;
        for (int h = 0; h < hd; h++) {
            db2[h] = dh2[h];
            for (int i = 0; i < hd; i++) dW2[(size_t)i * hd + h] = h1[i] * dh2[h];
        }
        for (int i = 0; i < hd; i++) {
            double s = 0;
            for (int h = 0; h < hd; h++) s += dh2[h] * W2[(size_t)i * hd + h];
            dh1[i] = (h1[i] > 0) ? s : 0;
        }
        for (int i = 0; i < sd; i++)
            for (int h = 0; h < hd; h++) dW1[(size_t)i * hd + h] = x[i] * dh1[h];
        for (int h = 0; h < hd; h++) db1[h] = dh1[h];
        auto nat = [&](std::vector<double>& w, std::vector<double>& acc,
                       const std::vector<double>& g) {
            for (size_t i = 0; i < w.size(); i++) {
                double a = acc[i] + g[i] * g[i]; acc[i] = a;
                w[i] -= lr * g[i] / (std::sqrt(a) + NATGRAD_EPS);
            }
        };
        nat(W1, accW1, dW1); nat(b1, accb1, db1);
        nat(W2, accW2, dW2); nat(b2, accb2, db2);
        nat(W3, accW3, dW3); nat(b3, accb3, db3);
    }

    void copyFrom(const QNet& o) {
        W1 = o.W1; b1 = o.b1; W2 = o.W2; b2 = o.b2; W3 = o.W3; b3 = o.b3;
    }
};

// ==================== MDP 环境 ====================
struct Env {
    const Mol* mol;
    Forces ff;
    Constraints con;
    std::vector<P3> original;
    std::vector<P3> pos;
    int nAtoms;
    std::mt19937_64& rng;
    bool done = false;

    Env(const Mol* m, std::mt19937_64& r) : mol(m), ff{m}, con{m}, rng(r),
        original(m->atoms.size()), pos(m->atoms.size()),
        nAtoms((int)m->atoms.size()) {
        for (size_t i = 0; i < m->atoms.size(); i++)
            original[i] = {m->atoms[i].x, m->atoms[i].y, m->atoms[i].z};
    }

    void reset() {
        std::normal_distribution<double> noise(0, 0.02);
        for (int i = 0; i < nAtoms; i++)
            for (int c = 0; c < 3; c++)
                pos[i][c] = original[i][c] + noise(rng);
        done = false;
    }

    // 路径系综：从某副本的最优构象(带扰动)重置，供 K 副本各自的探索轨迹使用
    void reset_from(const std::vector<P3>& ref) {
        std::normal_distribution<double> noise(0, 0.02);
        pos = ref;
        for (int i = 0; i < nAtoms; i++)
            for (int c = 0; c < 3; c++) pos[i][c] = ref[i][c] + noise(rng);
        done = false;
    }

    // 状态向量：每原子 [Z,q,m=1,nH,sp,sp2,sp3] + 每键 [b,r,same,Zi,Zj]
    std::vector<double> state() const {
        std::vector<double> s;
        s.reserve(mol->atoms.size() * 7 + mol->bondIJ.size() * 5);
        for (auto& a : mol->atoms) {
            s.push_back(a.Z);
            s.push_back(a.q);
            s.push_back(1.0);
            s.push_back(a.n_H);
            s.push_back(a.hyb == 0 ? 1 : 0);
            s.push_back(a.hyb == 1 ? 1 : 0);
            s.push_back(a.hyb == 2 ? 1 : 0);
        }
        for (size_t b = 0; b < mol->bondIJ.size(); b++) {
            int i = mol->bondIJ[b][0], j = mol->bondIJ[b][1];
            s.push_back(mol->bondOrder[b]);
            s.push_back(dist(pos[i], pos[j]));
            s.push_back(mol->atoms[i].symbol == mol->atoms[j].symbol ? 1 : 0);
            s.push_back(mol->atoms[i].Z);
            s.push_back(mol->atoms[j].Z);
        }
        return s;
    }

    void applyAction(int action) {
        std::normal_distribution<double> n005(0, 0.05), n002(0, 0.02);
        if (action == 0 || action == 1) {
            auto& nd = (action == 0) ? n005 : n002;
            for (int i = 0; i < nAtoms; i++)
                for (int c = 0; c < 3; c++) pos[i][c] += nd(rng);
        } else if (action == 2) {
            if (!mol->bondIJ.empty()) {
                // 键长修正：50% 优先修复错误惩罚最大的键（错误集直接引导搜索），
                // 50% 随机选键保持探索。优先级只看错误集惩罚——芳香键的 FF r0
                // 失配(约-17pm)会淹没任何键能偏差排序；错误键惩罚在正集目标处
                // 仍有 ~2，可把过冲构型持续拉回 r0。无错误集时优先级全 0，
                // 退化为纯随机选键（与原实现一致）。
                size_t bSel = (size_t)-1;
                {
                    std::uniform_real_distribution<double> coin(0.0, 1.0);
                    if (coin(rng) < 0.5) {
                        double bestP = 0.0;
                        for (size_t b = 0; b < mol->bondIJ.size(); b++) {
                            int bi = mol->bondIJ[b][0], bj = mol->bondIJ[b][1];
                            auto it = mol->errR0.find(mol->refKey(bi, bj));
                            if (it == mol->errR0.end()) continue;
                            double r = dist(pos[bi], pos[bj]) * 100.0;
                            double d = (r - it->second.first) / 10.0;
                            double p = it->second.second * std::exp(-d * d);
                            if (p > bestP + 1e-12) { bestP = p; bSel = b; }
                        }
                    }
                    if (bSel == (size_t)-1) {
                        std::uniform_int_distribution<int> ub(0, (int)mol->bondIJ.size() - 1);
                        bSel = (size_t)ub(rng);
                    }
                }
                int i = mol->bondIJ[bSel][0], j = mol->bondIJ[bSel][1];
                double r = dist(pos[i], pos[j]);
                double r0 = mol->bondR0pm(bSel) / 100.0;
                double dr = r0 - r;
                if (std::fabs(dr) > 1e-6) {
                    double inv = 1.0 / (r + 1e-8);
                    double dx = (pos[i][0]-pos[j][0]) * inv, dy = (pos[i][1]-pos[j][1]) * inv,
                           dz = (pos[i][2]-pos[j][2]) * inv;
                    for (int c = 0; c < 3; c++) {
                        double d = (c == 0 ? dx : c == 1 ? dy : dz) * 0.3 * dr;
                        pos[i][c] += d;
                        pos[j][c] -= d;
                    }
                }
            }
        } else if (action == 3) {
            std::uniform_int_distribution<int> ua(0, nAtoms - 1);
            int idx = ua(rng);
            for (int c = 0; c < 3; c++) pos[idx][c] += n002(rng);
        } else if (action == 4) {
            double cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < nAtoms; i++) { cx += pos[i][0]; cy += pos[i][1]; cz += pos[i][2]; }
            cx /= nAtoms; cy /= nAtoms; cz /= nAtoms;
            for (int i = 0; i < nAtoms; i++) {
                pos[i][0] = (pos[i][0] - cx) * 0.99 + cx;
                pos[i][1] = (pos[i][1] - cy) * 0.99 + cy;
                pos[i][2] = (pos[i][2] - cz) * 0.99 + cz;
            }
        } else if (action == 5) {
            if (!mol->nonbonded.empty()) {
                std::uniform_int_distribution<int> up(0, (int)mol->nonbonded.size() - 1);
                auto& pr = mol->nonbonded[up(rng)];
                int i = pr[0], j = pr[1];
                double r = dist(pos[i], pos[j]);
                double rmin = mol->atoms[i].e.vdw_ang + mol->atoms[j].e.vdw_ang;
                double overlap = rmin - r;
                if (overlap > 0) {
                    double inv = 1.0 / (r + 1e-8);
                    double dx = (pos[i][0]-pos[j][0]) * inv * 0.5 * overlap;
                    double dy = (pos[i][1]-pos[j][1]) * inv * 0.5 * overlap;
                    double dz = (pos[i][2]-pos[j][2]) * inv * 0.5 * overlap;
                    pos[i][0] += dx; pos[i][1] += dy; pos[i][2] += dz;
                    pos[j][0] -= dx; pos[j][1] -= dy; pos[j][2] -= dz;
                }
            }
        }
    }

    double reward() const {
        double E_ff = ff.total(pos);
        double E_c = con.energy(pos, ff);
        double E_err = ff.errPenalty(pos);
        int V = con.totalViol(pos, ff);
        double n = std::max((double)nAtoms, 1.0);
        double E_ff_c = std::max(0.0, std::min(1e6, E_ff));
        double E_c_c = std::max(0.0, std::min(1e3, E_c));
        double E_err_c = std::max(0.0, std::min(1e3, E_err));
        double rew = -0.1 * std::log(1.0 + E_ff_c / n)
                   - 0.5 * std::log(1.0 + E_c_c / n)
                   - 2.0 * std::log(1.0 + E_err_c / n)   // 错误集惩罚（介于 E_c 与违规项之间）
                   - 5.0 * std::log(1.0 + 10.0 * V / n);
        return std::max(-50.0, std::min(50.0, rew));
    }

    void step(int action) {
        applyAction(action);
        done = con.isValid(pos, ff);
    }
};

// ==================== 输入解析 ====================
static std::vector<std::string> splitWS(const std::string& s) {
    std::vector<std::string> out;
    size_t i = 0;
    while (i < s.size()) {
        while (i < s.size() && isspace((unsigned char)s[i])) i++;
        size_t j = i;
        while (j < s.size() && !isspace((unsigned char)s[j])) j++;
        if (j > i) out.push_back(s.substr(i, j - i));
        i = j;
    }
    return out;
}

int wmain(int argc, wchar_t** argv) {
    if (argc < 2) { fwprintf(stderr, L"用法: molopt.exe <输入文件>\n"); return 1; }
    FILE* fin = nullptr;
    if (_wfopen_s(&fin, argv[1], L"rb") != 0 || !fin) { fwprintf(stderr, L"无法打开输入文件\n"); return 1; }

    Mol mol;
    int episodes = 0, steps = 60;
    uint64_t seed = 0;
    bool haveSeed = false;
    int ensembleIn = 4;        // 路径系综并行构象副本数 K
    double resampleIn = 0.3;   // 每轮玻尔兹曼重播种概率
    double tauIn = 1.0;        // Q-softmax 温度
    double entropyIn = 0.1;    // 路径熵正则系数
    {
        char buf[4096];
        std::vector<std::string> lines;
        while (fgets(buf, sizeof(buf), fin)) {
            std::string s(buf);
            while (!s.empty() && (s.back() == '\n' || s.back() == '\r')) s.pop_back();
            lines.push_back(s);
        }
        fclose(fin);
        size_t li = 0;
        auto nextTok = [&]() -> std::vector<std::string> {
            while (li < lines.size() && lines[li].empty()) li++;
            return li < lines.size() ? splitWS(lines[li++]) : std::vector<std::string>{};
        };
        auto t = nextTok();
        if (t.empty() || t[0] != "NATOMS") { fwprintf(stderr, L"缺少NATOMS\n"); return 1; }
        int na = atoi(t[1].c_str());
        for (int i = 0; i < na; i++) {
            auto a = nextTok();
            if (a.size() < 6) { fwprintf(stderr, L"原子行不完整\n"); return 1; }
            AtomIn at;
            at.symbol = a[0];
            at.q = atof(a[1].c_str());
            at.Z = atoi(a[2].c_str());
            at.x = atof(a[3].c_str());
            at.y = atof(a[4].c_str());
            at.z = atof(a[5].c_str());
            at.e = elemOf(at.symbol);
            mol.atoms.push_back(at);
        }
        t = nextTok();
        if (t.empty() || t[0] != "NBONDS") { fwprintf(stderr, L"缺少NBONDS\n"); return 1; }
        int nb = atoi(t[1].c_str());
        for (int i = 0; i < nb; i++) {
            auto b = nextTok();
            if (b.size() < 3) { fwprintf(stderr, L"键行不完整\n"); return 1; }
            mol.bondIJ.push_back({atoi(b[0].c_str()), atoi(b[1].c_str())});
            mol.bondOrder.push_back(atof(b[2].c_str()));
        }
        while (true) {
            auto p = nextTok();
            if (p.empty()) break;
            if (p[0] == "EPISODES" && p.size() > 1) episodes = atoi(p[1].c_str());
            else if (p[0] == "STEPS" && p.size() > 1) steps = atoi(p[1].c_str());
            else if (p[0] == "SEED" && p.size() > 1) { seed = strtoull(p[1].c_str(), nullptr, 10); haveSeed = true; }
            else if (p[0] == "ENSEMBLE" && p.size() > 1) ensembleIn = std::max(1, atoi(p[1].c_str()));
            else if (p[0] == "RESAMPLE" && p.size() > 1) resampleIn = atof(p[1].c_str());
            else if (p[0] == "TAU" && p.size() > 1) tauIn = atof(p[1].c_str());
            else if (p[0] == "ENTROPY" && p.size() > 1) entropyIn = atof(p[1].c_str());
            else if (p[0] == "REFBOND" && p.size() > 3) {
                // 实验参考键长：REFBOND <i> <j> <r0_pm>，覆盖共价半径求和
                int i = atoi(p[1].c_str()), j = atoi(p[2].c_str());
                double r0 = atof(p[3].c_str());
                if (i >= 0 && j >= 0 && i < (int)mol.atoms.size() && j < (int)mol.atoms.size())
                    mol.refR0[Mol::refKey(i, j)] = r0;
            }
            else if (p[0] == "ERRBOND" && p.size() > 4) {
                // 错误集键长惩罚：ERRBOND <i> <j> <r_err_pm> <w>
                int i = atoi(p[1].c_str()), j = atoi(p[2].c_str());
                double rErr = atof(p[3].c_str()), w = atof(p[4].c_str());
                if (i >= 0 && j >= 0 && i < (int)mol.atoms.size() && j < (int)mol.atoms.size())
                    mol.errR0[Mol::refKey(i, j)] = {rErr, w};
            }
        }
    }
    mol.buildTopology();

    LARGE_INTEGER f, t0, t1;
    QueryPerformanceFrequency(&f);
    QueryPerformanceCounter(&t0);

    if (!haveSeed) seed = std::random_device{}();
    std::mt19937_64 rng(seed);

    Env env(&mol, rng);
    env.pos = env.original;

    // ---------- 仅评估模式 ----------
    auto printEnergies = [&](const std::vector<P3>& p) {
        printf("{");
        printf("\"bond\":%.17g,", env.ff.bondEnergy(p));
        printf("\"angle\":%.17g,", env.ff.angleEnergy(p));
        printf("\"torsion\":%.17g,", env.ff.torsionEnergy(p));
        printf("\"vdw\":%.17g,", env.ff.vdwEnergy(p));
        printf("\"electrostatic\":%.17g,", env.ff.electrostaticEnergy(p));
        printf("\"total\":%.17g,", env.ff.total(p));
        printf("\"error_penalty\":%.17g,", env.ff.errPenalty(p));
        printf("\"constraint_energy\":%.17g,", env.con.energy(p, env.ff));
        printf("\"violations\":%d,", env.con.totalViol(p, env.ff));
        printf("\"is_valid\":%s", env.con.isValid(p, env.ff) ? "true" : "false");
        printf("}");
    };

    if (episodes <= 0) {
        QueryPerformanceCounter(&t1);
        double ms = (t1.QuadPart - t0.QuadPart) * 1000.0 / f.QuadPart;
        printf("{\"mode\":\"energy\",\"n_atoms\":%d,\"n_bonds\":%d,",
               (int)mol.atoms.size(), (int)mol.bondIJ.size());
        printf("\"n_angles\":%d,\"n_dihedrals\":%d,",
               (int)mol.angles.size(), (int)mol.dihedrals.size());
        printf("\"energies\":");
        printEnergies(env.pos);
        printf(",\"elapsed_ms\":%.6g}", ms);
        return 0;
    }

    // ---------- 训练模式（路径系综+自然梯度+路径熵；超参与 run_optimization 一致） ----------
    const int hidden = 64, actionDim = 6;
    const double lr = 0.01, gamma = 0.99;
    const double epsDecay = 0.9, epsMin = 0.05;
    int stateDim = (int)(mol.atoms.size() * 7 + mol.bondIJ.size() * 5);
    QNet qnet(stateDim, hidden, actionDim, rng);
    QNet target(stateDim, hidden, actionDim, rng);
    target.copyFrom(qnet);
    double epsilon = 1.0;
    std::vector<double> epRewards;
    std::vector<double> epBest;   // 每个 episode 结束时系综历史最优分数（能量+错误惩罚）
    int episodesRun = 0;
    int foundValid = 0;

    const int ensemble = ensembleIn;
    const double resampleProb = resampleIn;
    const double tau = tauIn;
    const double entropyLambda = entropyIn;

    std::uniform_real_distribution<double> u01(0.0, 1.0);
    // 路径系综：K 副本各记历史最优构象与分数（优先有效结构，其次总能量最低）。
    using PosEns = std::vector<std::vector<P3>>;   // K 副本 × nAtoms
    PosEns ensBest(ensemble, env.original);
    std::vector<double> ensScore(ensemble, 0.0);
    {
        double s0 = (env.con.isValid(env.original, env.ff) ? 0.0 : 1e12)
                    + env.ff.total(env.original) + env.ff.errPenalty(env.original);
        for (int k = 0; k < ensemble; k++) ensScore[k] = s0;
    }
    // 动作选择：ε-随机探索 或 Q 值温度 softmax 采样（路径系综软策略）
    auto selectAction = [&](const std::vector<double>& st) -> int {
        if (u01(rng) < epsilon) {
            std::uniform_int_distribution<int> ua(0, actionDim - 1);
            return ua(rng);
        }
        auto p = qnet.softmax_probs(st, tau);
        double r = u01(rng), cum = 0;
        int a = actionDim - 1;
        for (int i = 0; i < actionDim; i++)
            if ((cum += p[i]) >= r) { a = i; break; }
        return a;
    };

    for (int ep = 0; ep < episodes; ep++) {
        episodesRun++;
        double totalR = 0;
        for (int k = 0; k < ensemble; k++) {
            env.reset_from(ensBest[k]);
            auto st = env.state();
            for (int s = 0; s < steps; s++) {
                int action = selectAction(st);
                env.step(action);
                // 即时奖励叠加路径熵正则（抑制策略过早坍缩到单一动作）
                double rew = env.reward()
                           + entropyLambda * qnet.q_entropy(env.state(), tau);
                // 更新该副本的历史最优（含错误集惩罚，与 Python _score 一致）
                double sc = (env.con.isValid(env.pos, env.ff) ? 0.0 : 1e12)
                            + env.ff.total(env.pos) + env.ff.errPenalty(env.pos);
                if (sc < ensScore[k]) { ensScore[k] = sc; ensBest[k] = env.pos; }
                auto nst = env.state();
                double tgt = rew;
                if (!env.done) {
                    auto nq = target.predict(nst);
                    tgt = rew + gamma * *std::max_element(nq.begin(), nq.end());
                }
                qnet.update(st, action, tgt, lr);
                totalR += rew;
                st = nst;
                if (env.done) { foundValid = 1; break; }
                if (u01(rng) < 0.1) target.copyFrom(qnet);
            }
        }
        // 玻尔兹曼重播种：能量最高副本锚定到最低副本邻域，耦合 K 副本避免各自孤立陷入局部极小。
        // （Python 中 _boltzmann_reseed 每 episode 无条件调用，此处保持一致）
        {
            size_t kB = (size_t)(std::min_element(ensScore.begin(), ensScore.end())
                                 - ensScore.begin());
            size_t kW = (size_t)(std::max_element(ensScore.begin(), ensScore.end())
                                 - ensScore.begin());
            if (kB != kW) {
                std::normal_distribution<double> n005(0, 0.05);
                for (size_t i = 0; i < ensBest[kB].size(); i++)
                    for (int c = 0; c < 3; c++)
                        ensBest[kW][i][c] = ensBest[kB][i][c] + n005(rng);
                ensScore[kW] = std::numeric_limits<double>::infinity();
                env.pos = ensBest[kW];
            }
        }
        epRewards.push_back(totalR);
        epBest.push_back(*std::min_element(ensScore.begin(), ensScore.end()));
        epsilon = std::max(epsMin, epsilon * epsDecay);
        if (foundValid) break;
    }
    // 训练结束回到系综内历史最优构象
    size_t kBest = (size_t)(std::min_element(ensScore.begin(), ensScore.end())
                            - ensScore.begin());
    env.pos = ensBest[kBest];

    QueryPerformanceCounter(&t1);
    double ms = (t1.QuadPart - t0.QuadPart) * 1000.0 / f.QuadPart;

    // ---------- 输出结果 JSON ----------
    printf("{\"mode\":\"optimize\",\"backend\":\"cpp\",\"n_atoms\":%d,",
           (int)mol.atoms.size());
    printf("\"episodes_run\":%d,\"K\":%d,\"final_epsilon\":%.6g,", episodesRun,
           ensemble, epsilon);
    printf("\"episode_rewards\":[");
    for (size_t i = 0; i < epRewards.size(); i++)
        printf("%s%.17g", i ? "," : "", epRewards[i]);
    printf("],");
    printf("\"best_scores\":[");
    for (size_t i = 0; i < epBest.size(); i++)
        printf("%s%.17g", i ? "," : "", epBest[i]);
    printf("],");
    printf("\"energies\":");
    printEnergies(env.pos);
    printf(",");
    printf("\"coords\":[");
    for (size_t i = 0; i < env.pos.size(); i++)
        printf("%s[%.17g,%.17g,%.17g]", i ? "," : "",
               env.pos[i][0], env.pos[i][1], env.pos[i][2]);
    printf("],");
    printf("\"seed\":%llu,\"elapsed_ms\":%.6g}",
           (unsigned long long)seed, ms);
    return 0;
}
