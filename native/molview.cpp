// molview.cpp - 原生分子可视化窗口 (Win32 + OpenGL，零外部依赖)
// 功能：球棍/比例/线框模型、XYZ与MOL(V2000)格式、鼠标旋转/缩放/平移、氢原子开关、
//       元素图例、原子拾取与几何测量(M进入/空格提交/C清除)、悬停原子信息、MSAA抗锯齿、
//       背景渐变、BMP截图导出(S)、拖拽打开文件
//       FMO/电子云等值面渲染：同目录 <basename>_<tag>.{cub,cube,tri} 自动加载（B=开关FMO D=开关密度）
// 编译：cl /O2 /EHsc /utf-8 /DUNICODE /D_UNICODE molview.cpp（/utf-8必需：源码含中文，无BOM）
// 用法：molview.exe [文件路径(.xyz/.mol)]   （无参数时弹出打开对话框）

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <windowsx.h>
#include <commdlg.h>
#include <shellapi.h>
#include <imm.h>
#include <ole2.h>
#include <GL/gl.h>
#include <GL/glu.h>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>
#include <map>
#include <set>
#include <unordered_map>
#include <algorithm>
#include <thread>
#include <atomic>
#include <gdiplus.h>
using namespace Gdiplus;

#pragma comment(lib, "opengl32.lib")
#pragma comment(lib, "glu32.lib")
#pragma comment(lib, "imm32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "comdlg32.lib")
#pragma comment(lib, "gdiplus.lib")
#pragma comment(lib, "ole32.lib")

// 旧版SDK的gl.h缺少的WGL_ARB_pixel_format / 多重采样常量
#ifndef WGL_DRAW_TO_WINDOW_ARB
#define WGL_DRAW_TO_WINDOW_ARB      0x2001
#define WGL_SUPPORT_OPENGL_ARB      0x2010
#define WGL_DOUBLE_BUFFER_ARB       0x2011
#define WGL_PIXEL_TYPE_ARB          0x2013
#define WGL_COLOR_BITS_ARB          0x2014
#define WGL_DEPTH_BITS_ARB          0x2022
#define WGL_TYPE_RGBA_ARB           0x202B
#define WGL_SAMPLE_BUFFERS_ARB      0x2042
#define WGL_SAMPLES_ARB             0x2043
#endif
#ifndef GL_MULTISAMPLE
#define GL_MULTISAMPLE              0x809D
#endif
typedef BOOL (WINAPI *PFN_WGLCHOOSEPIXELFORMATARB)(HDC, const int*, const float*, UINT, int*, UINT*);

// ==================== 数学工具 ====================
struct Vec3 {
    float x, y, z;
    Vec3() : x(0), y(0), z(0) {}
    Vec3(float a, float b, float c) : x(a), y(b), z(c) {}
    Vec3 operator+(const Vec3& r) const { return {x + r.x, y + r.y, z + r.z}; }
    Vec3 operator-(const Vec3& r) const { return {x - r.x, y - r.y, z - r.z}; }
    Vec3 operator*(float s) const { return {x * s, y * s, z * s}; }
    float dot(const Vec3& r) const { return x * r.x + y * r.y + z * r.z; }
    Vec3 cross(const Vec3& r) const {
        return {y * r.z - z * r.y, z * r.x - x * r.z, x * r.y - y * r.x};
    }
    float len() const { return std::sqrt(x * x + y * y + z * z); }
    Vec3 normalized() const { float L = len(); return L > 1e-9f ? Vec3(x / L, y / L, z / L) : Vec3(0, 1, 0); }
};

// 四元数 (用于轨迹球旋转，避免万向节锁)
struct Quat {
    float w, x, y, z;
    Quat() : w(1), x(0), y(0), z(0) {}
    Quat(float w_, float x_, float y_, float z_) : w(w_), x(x_), y(y_), z(z_) {}
    Quat operator*(const Quat& r) const {
        return {
            w * r.w - x * r.x - y * r.y - z * r.z,
            w * r.x + x * r.w + y * r.z - z * r.y,
            w * r.y - x * r.z + y * r.w + z * r.x,
            w * r.z + x * r.y - y * r.x + z * r.w
        };
    }
    Quat conjugate() const { return Quat(w, -x, -y, -z); }
    // 用四元数旋转三维向量（v' = q v q*）
    Vec3 rotateVec(const Vec3& v) const {
        Vec3 u(x, y, z);
        Vec3 t = u.cross(v) * 2.0f;
        return v + t * w + u.cross(t);
    }
    void normalize() {
        float L = std::sqrt(w * w + x * x + y * y + z * z);
        if (L > 1e-9f) { w /= L; x /= L; y /= L; z /= L; }
    }
    void toMatrix(float m[16]) const {
        m[0] = 1 - 2 * (y * y + z * z); m[1] = 2 * (x * y + w * z);       m[2] = 2 * (x * z - w * y);       m[3] = 0;
        m[4] = 2 * (x * y - w * z);      m[5] = 1 - 2 * (x * x + z * z);   m[6] = 2 * (y * z + w * x);       m[7] = 0;
        m[8] = 2 * (x * z + w * y);      m[9] = 2 * (y * z - w * x);       m[10] = 1 - 2 * (x * x + y * y);  m[11] = 0;
        m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
    }
};

// ==================== 元素数据（颜色 + 半径，单位Å） ====================
struct ElementInfo {
    float r, g, b;
    float covalent;   // 共价半径（成键判断）
    float display;    // 球棍模型显示半径
    float vdw;        // 范德华半径（比例模型）
    const wchar_t* cnName;
};

static const std::map<std::string, ElementInfo> ELEMENTS = {
    {"H",  {1.00f, 1.00f, 1.00f, 0.31f, 0.22f, 1.20f, L"氢"}},
    {"He", {0.85f, 1.00f, 1.00f, 0.28f, 0.20f, 1.40f, L"氦"}},
    {"Li", {0.80f, 0.50f, 0.43f, 1.28f, 0.98f, 1.82f, L"锂"}},
    {"Be", {0.76f, 1.00f, 0.00f, 0.96f, 0.81f, 1.53f, L"铍"}},
    {"B",  {1.00f, 0.71f, 0.71f, 0.84f, 0.78f, 1.92f, L"硼"}},
    {"C",  {0.25f, 0.25f, 0.25f, 0.76f, 0.73f, 1.70f, L"碳"}},
    {"N",  {0.13f, 0.20f, 0.80f, 0.71f, 0.70f, 1.55f, L"氮"}},
    {"O",  {1.00f, 0.05f, 0.05f, 0.66f, 0.66f, 1.52f, L"氧"}},
    {"F",  {0.56f, 0.88f, 0.93f, 0.57f, 0.61f, 1.47f, L"氟"}},
    {"Ne", {0.70f, 0.89f, 0.96f, 0.58f, 0.56f, 1.54f, L"氖"}},
    {"Na", {0.67f, 0.36f, 0.95f, 1.66f, 1.20f, 2.27f, L"钠"}},
    {"Mg", {0.54f, 1.00f, 0.00f, 1.41f, 1.10f, 1.73f, L"镁"}},
    {"Al", {0.75f, 0.65f, 0.65f, 1.21f, 1.17f, 1.84f, L"铝"}},
    {"Si", {0.94f, 0.78f, 0.62f, 1.11f, 1.11f, 2.10f, L"硅"}},
    {"P",  {1.00f, 0.56f, 0.00f, 1.07f, 1.05f, 1.80f, L"磷"}},
    {"S",  {1.00f, 0.78f, 0.19f, 1.05f, 1.03f, 1.80f, L"硫"}},
    {"Cl", {0.20f, 0.80f, 0.20f, 1.02f, 1.00f, 1.75f, L"氯"}},
    {"K",  {0.56f, 0.25f, 0.83f, 2.03f, 1.40f, 2.75f, L"钾"}},
    {"Ca", {0.24f, 1.00f, 0.00f, 1.76f, 1.35f, 2.31f, L"钙"}},
    {"Ti", {0.75f, 0.76f, 0.78f, 1.60f, 1.28f, 2.11f, L"钛"}},
    {"Fe", {0.87f, 0.35f, 0.30f, 1.32f, 1.15f, 2.04f, L"铁"}},
    {"Ni", {0.31f, 0.82f, 0.31f, 1.24f, 1.10f, 1.97f, L"镍"}},
    {"Cu", {0.78f, 0.50f, 0.24f, 1.32f, 1.15f, 1.96f, L"铜"}},
    {"Zn", {0.49f, 0.50f, 0.69f, 1.22f, 1.12f, 2.01f, L"锌"}},
    {"Se", {1.00f, 0.63f, 0.00f, 1.20f, 1.13f, 1.90f, L"硒"}},
    {"Br", {0.50f, 0.30f, 0.10f, 1.20f, 1.14f, 1.85f, L"溴"}},
    {"Sn", {0.65f, 0.65f, 0.65f, 1.39f, 1.24f, 2.17f, L"锡"}},
    {"Te", {0.83f, 0.47f, 0.00f, 1.38f, 1.27f, 2.06f, L"碲"}},
    {"I",  {0.40f, 0.00f, 0.50f, 1.39f, 1.28f, 1.98f, L"碘"}},
    {"W",  {0.33f, 0.33f, 0.33f, 1.62f, 1.30f, 2.10f, L"钨"}},
    {"Pb", {0.34f, 0.35f, 0.44f, 1.46f, 1.30f, 2.02f, L"铅"}},
    {"Xe", {0.26f, 0.62f, 0.69f, 1.40f, 1.28f, 2.16f, L"氙"}},
};

static const ElementInfo UNKNOWN_ELEM = {0.80f, 0.70f, 0.90f, 1.00f, 0.90f, 1.80f, L"?"};

static const ElementInfo& elemInfo(const std::string& sym) {
    auto it = ELEMENTS.find(sym);
    return it != ELEMENTS.end() ? it->second : UNKNOWN_ELEM;
}

// ==================== 分子数据 ====================
struct Atom {
    std::string symbol;
    Vec3 pos;
};

struct Bond {
    int a, b;   // 原子索引
    int order;  // 1/2/3
    bool fromMol; // 来自MOL文件显式键（不随氢开关隐藏）
};

struct Molecule {
    std::vector<Atom> atoms;
    std::vector<Bond> bonds;
    std::string title;
    Vec3 center;
    float radius = 1.0f; // 包围球半径
    bool molHasExplicitBonds = false;

    void computeExtent() {
        center = Vec3(0, 0, 0);
        if (atoms.empty()) { radius = 1.0f; return; }
        for (auto& a : atoms) center = center + a.pos;
        center = center * (1.0f / atoms.size());
        radius = 0.1f;
        for (auto& a : atoms) radius = std::max(radius, (a.pos - center).len());
    }
};

// ==================== 文件解析 ====================
static std::string trim(const std::string& s) {
    size_t b = s.find_first_not_of(" \t\r\n");
    if (b == std::string::npos) return "";
    size_t e = s.find_last_not_of(" \t\r\n");
    return s.substr(b, e - b + 1);
}

static std::vector<std::string> splitWS(const std::string& s) {
    std::vector<std::string> out;
    size_t i = 0;
    while (i < s.size()) {
        while (i < s.size() && isspace((unsigned char)s[i])) i++;
        size_t st = i;
        while (i < s.size() && !isspace((unsigned char)s[i])) i++;
        if (i > st) out.push_back(s.substr(st, i - st));
    }
    return out;
}

// 截取定宽字段（MOL格式每字段3字符）
static std::string fld(const std::string& s, int pos, int len) {
    if (pos >= (int)s.size()) return "";
    return trim(s.substr(pos, len));
}

static bool parseMol(const std::string& path, Molecule& mol) {
    FILE* f = nullptr;
    if (fopen_s(&f, path.c_str(), "rb") != 0 || !f) return false;
    std::string content;
    char buf[4096];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) content.append(buf, n);
    fclose(f);

    std::vector<std::string> lines;
    size_t pos = 0;
    while (pos <= content.size()) {
        size_t e = content.find('\n', pos);
        std::string line = (e == std::string::npos) ? content.substr(pos) : content.substr(pos, e - pos);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
        if (e == std::string::npos) break;
        pos = e + 1;
    }
    if (lines.size() < 5) return false;

    mol.title = trim(lines[0]);
    // 计数行：标准为第4行(lines[3]=标题+程序行+空注释行之后)，容错支持缺空注释行的第3行
    int countsLine = -1, nAtoms = 0, nBonds = 0;
    for (int li = 2; li <= 3 && li < (int)lines.size() && countsLine < 0; li++) {
        int a = atoi(fld(lines[li], 0, 3).c_str());
        int b = atoi(fld(lines[li], 3, 3).c_str());
        if (a > 0 && a <= 5000 && b >= 0 && b <= 5000) { nAtoms = a; nBonds = b; countsLine = li; }
    }
    if (countsLine < 0) {
        // 再容错：空格分词式计数行（手写/非标准对齐文件）
        for (int li = 2; li <= 3 && li < (int)lines.size() && countsLine < 0; li++) {
            auto toks = splitWS(lines[li]);
            if (toks.size() >= 2) {
                int a = atoi(toks[0].c_str()), b = atoi(toks[1].c_str());
                if (a > 0 && a <= 5000 && b >= 0 && b <= 5000) { nAtoms = a; nBonds = b; countsLine = li; }
            }
        }
    }
    if (countsLine < 0) return false;

    for (int i = 0; i < nAtoms && (size_t)(countsLine + 1 + i) < lines.size(); i++) {
        const std::string& L = lines[countsLine + 1 + i];
        if (L.size() < 31) return false;
        float x = (float)atof(fld(L, 0, 10).c_str());
        float y = (float)atof(fld(L, 10, 10).c_str());
        float z = (float)atof(fld(L, 20, 10).c_str());
        std::string sym = fld(L, 31, 3);
        if (sym.empty()) return false;
        if (sym.size() == 2) sym[1] = (char)tolower((unsigned char)sym[1]);
        mol.atoms.push_back({sym, {x, y, z}});
    }
    for (int i = 0; i < nBonds && (size_t)(countsLine + 1 + nAtoms + i) < lines.size(); i++) {
        const std::string& L = lines[countsLine + 1 + nAtoms + i];
        if (L.size() < 9) break;
        int a = atoi(fld(L, 0, 3).c_str()) - 1;
        int b = atoi(fld(L, 3, 3).c_str()) - 1;
        int ord = atoi(fld(L, 6, 3).c_str());
        if (a >= 0 && b >= 0 && a < nAtoms && b < nAtoms)
            mol.bonds.push_back({a, b, std::max(1, std::min(3, ord)), true});
    }
    mol.molHasExplicitBonds = true;
    return !mol.atoms.empty();
}

static bool parseXyz(const std::string& path, Molecule& mol) {
    FILE* f = nullptr;
    if (fopen_s(&f, path.c_str(), "rb") != 0 || !f) return false;
    std::string content;
    char buf[4096];
    size_t n;
    while ((n = fread(buf, 1, sizeof(buf), f)) > 0) content.append(buf, n);
    fclose(f);

    std::vector<std::string> lines;
    size_t pos = 0;
    while (pos <= content.size()) {
        size_t e = content.find('\n', pos);
        std::string line = (e == std::string::npos) ? content.substr(pos) : content.substr(pos, e - pos);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        lines.push_back(line);
        if (e == std::string::npos) break;
        pos = e + 1;
    }
    if (lines.empty()) return false;

    int nAtoms = atoi(trim(lines[0]).c_str());
    if (nAtoms <= 0 || nAtoms > 5000) return false;
    if (lines.size() >= 2) mol.title = trim(lines[1]);

    for (int i = 0; i < nAtoms && (size_t)(2 + i) < lines.size(); i++) {
        auto toks = splitWS(lines[2 + i]);
        if (toks.size() < 4) continue;
        std::string sym = toks[0];
        if (sym.size() >= 2 && islower((unsigned char)sym[1]) && isupper((unsigned char)sym[0])) {
            // 已是标准形式
        } else if (sym.size() == 2 && isupper((unsigned char)sym[0])) {
            sym[1] = (char)tolower((unsigned char)sym[1]);
        } else if (!sym.empty()) {
            sym[0] = (char)toupper((unsigned char)sym[0]);
        }
        mol.atoms.push_back({sym, {(float)atof(toks[1].c_str()),
                                    (float)atof(toks[2].c_str()),
                                    (float)atof(toks[3].c_str())}});
    }
    return !mol.atoms.empty();
}

// XYZ文件按距离判断成键
static void detectBonds(Molecule& mol) {
    if (mol.molHasExplicitBonds) return;
    const float TOL = 1.20f;  // 收紧共价半径容差，避免把非键空间接触(如产物帧 CO2碳…Al 2.58Å)误判为键
    for (size_t i = 0; i < mol.atoms.size(); i++) {
        for (size_t j = i + 1; j < mol.atoms.size(); j++) {
            const std::string& si = mol.atoms[i].symbol;
            const std::string& sj = mol.atoms[j].symbol;
            if (si == "H" && sj == "H") continue; // 排除H-H
            float d = (mol.atoms[i].pos - mol.atoms[j].pos).len();
            float limit = (elemInfo(si).covalent + elemInfo(sj).covalent) * TOL;
            if (d < limit && d > 0.4f)
                mol.bonds.push_back({(int)i, (int)j, 1, false});
        }
    }
}

static bool loadMolecule(const std::string& path, Molecule& mol) {
    mol = Molecule();
    std::string lower = path;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    bool ok = false;
    if (lower.size() > 4 && lower.substr(lower.size() - 4) == ".mol") ok = parseMol(path, mol);
    else ok = parseXyz(path, mol);
    if (!ok) return false;
    detectBonds(mol);
    mol.computeExtent();
    return true;
}

// ==================== 查看器状态 ====================
enum RenderMode { MODE_BALLSTICK = 0, MODE_SPACEFILL, MODE_WIRE };

// ==================== Cube 文件（FMO/电子云数据） ====================
static const float BOHR = 0.52917721092f; // Bohr → Å

struct CubeData {
    int nx = 0, ny = 0, nz = 0;
    float ox = 0, oy = 0, oz = 0;   // 原点坐标 (Å)
    float sx = 1, sy = 1, sz = 1;   // 网格步长 (Å)
    std::vector<float> vals;        // (nz,ny,nx) 顺序，x 最快，值单位 a.u.
    float vmin = 0, vmax = 0;
};

// 解析 Gaussian/Multiwfn 格式 cube 文件（头含 1~2 行注释，需跳过）
static bool parseCube(const std::string& path, CubeData& c) {
    FILE* f = nullptr;
    if (fopen_s(&f, path.c_str(), "r") != 0 || !f) return false;
    char buf[1024];
    // 跳过注释行（Gaussian 1 行 / Multiwfn 2 行），直到数字开头行
    int guard = 0;
    while (fgets(buf, sizeof(buf), f)) {
        std::string s = trim(buf);
        if (!s.empty() && (isdigit((unsigned char)s[0]) || s[0] == '+' || s[0] == '-' || s[0] == '.'))
            break;
        if (++guard > 16) { fclose(f); return false; }
    }
    int natoms = 0;
    float ox = 0, oy = 0, oz = 0;
    if (sscanf(buf, "%d %f %f %f", &natoms, &ox, &oy, &oz) < 4) { fclose(f); return false; }
    int nx = 0, ny = 0, nz = 0;
    float sx = 1, sy = 1, sz = 1;
    // cube 步长行格式 "n vx vy vz"：对角线分量分别在第 2/3/4 位，必须取各自分量
    for (int i = 0; i < 3; i++) {
        if (!fgets(buf, sizeof(buf), f)) { fclose(f); return false; }
        int n = 0; float v0 = 0, v1 = 0, v2 = 0;
        int nf = sscanf(buf, "%d %f %f %f", &n, &v0, &v1, &v2);
        if (nf < 2) { fclose(f); return false; }
        if (i == 0) { nx = n; sx = v0; }
        else if (i == 1) { ny = n; sy = v1; }
        else { nz = n; sz = v2; }
    }
    for (int i = 0; i < natoms; i++) {
        if (!fgets(buf, sizeof(buf), f)) { fclose(f); return false; }
    }
    if (nx <= 0 || ny <= 0 || nz <= 0 || nx > 600 || ny > 600 || nz > 600) { fclose(f); return false; }
    size_t total = (size_t)nx * ny * nz;
    c.nx = nx; c.ny = ny; c.nz = nz;
    c.ox = ox * BOHR; c.oy = oy * BOHR; c.oz = oz * BOHR;
    c.sx = sx * BOHR; c.sy = sy * BOHR; c.sz = sz * BOHR;
    // Multiwfn 导出的 cube 数据按 z 最快写入：linear = ix*ny*nz + iy*nz + iz
    // 内部按 x 最快存储（linear = iz*ny*nx + iy*nx + ix），读取时重排
    c.vals.assign(total, 0.0f);
    float mn = 1e30f, mx = -1e30f;
    size_t fi = 0;
    while (fi < total && fgets(buf, sizeof(buf), f)) {
        char* p = buf;
        while (fi < total && *p) {
            while (*p && isspace((unsigned char)*p)) p++;
            if (!*p) break;
            char* end = nullptr;
            float v = (float)strtod(p, &end);
            if (end == p) break;
            size_t iz = fi % (size_t)nz;
            size_t iy = (fi / (size_t)nz) % (size_t)ny;
            size_t ix = fi / ((size_t)nz * (size_t)ny);
            c.vals[iz * (size_t)ny * nx + iy * nx + ix] = v;
            if (v < mn) mn = v;
            if (v > mx) mx = v;
            fi++;
            p = end;
        }
    }
    fclose(f);
    if (fi < total) return false;
    c.vmin = mn; c.vmax = mx;
    return true;
}

// ==================== Marching Cubes（Paul Bourke 经典表） ====================
static const int edgeTable[256] = {
0x0,0x109,0x203,0x30a,0x406,0x50f,0x605,0x70c,0x80c,0x905,0xa0f,0xb06,0xc0a,0xd03,0xe09,0xf00,
0x190,0x99,0x393,0x29a,0x596,0x49f,0x795,0x69c,0x99c,0x895,0xb9f,0xa96,0xd9a,0xc93,0xf99,0xe90,
0x230,0x339,0x33,0x13a,0x636,0x73f,0x435,0x53c,0xa3c,0xb35,0x83f,0x936,0xe3a,0xf33,0xc39,0xd30,
0x3a0,0x2a9,0x1a3,0xaa,0x7a6,0x6af,0x5a5,0x4ac,0xbac,0xaa5,0x9af,0x8a6,0xfaa,0xea3,0xda9,0xca0,
0x460,0x569,0x663,0x76a,0x66,0x16f,0x265,0x36c,0xc6c,0xd65,0xe6f,0xf66,0x86a,0x963,0xa69,0xb60,
0x5f0,0x4f9,0x7f3,0x6fa,0x1f6,0xff,0x3f5,0x2fc,0xdfc,0xcf5,0xfff,0xef6,0x9fa,0x8f3,0xbf9,0xaf0,
0x650,0x759,0x453,0x55a,0x256,0x35f,0x55,0x15c,0xe5c,0xf55,0xc5f,0xd56,0xa5a,0xb53,0x859,0x950,
0x7c0,0x6c9,0x5c3,0x4ca,0x3c6,0x2cf,0x1c5,0xcc,0xfcc,0xec5,0xdcf,0xcc6,0xbca,0xac3,0x9c9,0x8c0,
0x8c0,0x9c9,0xac3,0xbca,0xcc6,0xdcf,0xec5,0xfcc,0xcc,0x1c5,0x2cf,0x3c6,0x4ca,0x5c3,0x6c9,0x7c0,
0x950,0x859,0xb53,0xa5a,0xd56,0xc5f,0xf55,0xe5c,0x15c,0x55,0x35f,0x256,0x55a,0x453,0x759,0x650,
0xaf0,0xbf9,0x8f3,0x9fa,0xef6,0xfff,0xcf5,0xdfc,0x2fc,0x3f5,0xff,0x1f6,0x6fa,0x7f3,0x4f9,0x5f0,
0xb60,0xa69,0x963,0x86a,0xf66,0xe6f,0xd65,0xc6c,0x36c,0x265,0x16f,0x66,0x76a,0x663,0x569,0x460,
0xca0,0xda9,0xea3,0xfaa,0x8a6,0x9af,0xaa5,0xbac,0x4ac,0x5a5,0x6af,0x7a6,0xaa,0x1a3,0x2a9,0x3a0,
0xd30,0xc39,0xf33,0xe3a,0x936,0x83f,0xb35,0xa3c,0x53c,0x435,0x73f,0x636,0x13a,0x33,0x339,0x230,
0xe90,0xf99,0xc93,0xd9a,0xa96,0xb9f,0x895,0x99c,0x69c,0x795,0x49f,0x596,0x29a,0x393,0x99,0x190,
0xf00,0xe09,0xd03,0xc0a,0xb06,0xa0f,0x905,0x80c,0x70c,0x605,0x50f,0x406,0x30a,0x203,0x109,0x0};

static const int triTable[256][16] = {
{-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,1,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,8,3,9,8,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,1,2,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,2,10,0,2,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{2,8,3,2,10,8,10,9,8,-1,-1,-1,-1,-1,-1,-1},
{3,11,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,11,2,8,11,0,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,9,0,2,3,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,11,2,1,9,11,9,8,11,-1,-1,-1,-1,-1,-1,-1},
{3,10,1,11,10,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,10,1,0,8,10,8,11,10,-1,-1,-1,-1,-1,-1,-1},
{3,9,0,3,11,9,11,10,9,-1,-1,-1,-1,-1,-1,-1},
{9,8,10,10,8,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,7,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,3,0,7,3,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,1,9,8,4,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,1,9,4,7,1,7,3,1,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,8,4,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,4,7,3,0,4,1,2,10,-1,-1,-1,-1,-1,-1,-1},
{9,2,10,9,0,2,8,4,7,-1,-1,-1,-1,-1,-1,-1},
{2,10,9,2,9,7,2,7,3,7,9,4,-1,-1,-1,-1},
{8,4,7,3,11,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{11,4,7,11,2,4,2,0,4,-1,-1,-1,-1,-1,-1,-1},
{9,0,1,8,4,7,2,3,11,-1,-1,-1,-1,-1,-1,-1},
{4,7,11,9,4,11,9,11,2,9,2,1,-1,-1,-1,-1},
{3,10,1,3,11,10,7,8,4,-1,-1,-1,-1,-1,-1,-1},
{1,11,10,1,4,11,1,0,4,7,11,4,-1,-1,-1,-1},
{4,7,8,9,0,11,9,11,10,11,0,3,-1,-1,-1,-1},
{4,7,11,4,11,9,9,11,10,-1,-1,-1,-1,-1,-1,-1},
{9,5,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,5,4,0,8,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,5,4,1,5,0,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{8,5,4,8,3,5,3,1,5,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,9,5,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,0,8,1,2,10,4,9,5,-1,-1,-1,-1,-1,-1,-1},
{5,2,10,5,4,2,4,0,2,-1,-1,-1,-1,-1,-1,-1},
{2,10,5,3,2,5,3,5,4,3,4,8,-1,-1,-1,-1},
{9,5,4,2,3,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,11,2,0,8,11,4,9,5,-1,-1,-1,-1,-1,-1,-1},
{0,5,4,0,1,5,2,3,11,-1,-1,-1,-1,-1,-1,-1},
{2,1,5,2,5,8,2,8,11,4,8,5,-1,-1,-1,-1},
{10,3,11,10,1,3,9,5,4,-1,-1,-1,-1,-1,-1,-1},
{4,9,5,0,8,1,8,10,1,8,11,10,-1,-1,-1,-1},
{5,4,0,5,0,11,5,11,10,11,0,3,-1,-1,-1,-1},
{5,4,8,5,8,10,10,8,11,-1,-1,-1,-1,-1,-1,-1},
{9,7,8,5,7,9,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,3,0,9,5,3,5,7,3,-1,-1,-1,-1,-1,-1,-1},
{0,7,8,0,1,7,1,5,7,-1,-1,-1,-1,-1,-1,-1},
{1,5,3,3,5,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,7,8,9,5,7,10,1,2,-1,-1,-1,-1,-1,-1,-1},
{10,1,2,9,5,0,5,3,0,5,7,3,-1,-1,-1,-1},
{8,0,2,8,2,5,8,5,7,10,5,2,-1,-1,-1,-1},
{2,10,5,2,5,3,3,5,7,-1,-1,-1,-1,-1,-1,-1},
{7,9,5,7,8,9,3,11,2,-1,-1,-1,-1,-1,-1,-1},
{9,5,7,9,7,2,9,2,0,2,7,11,-1,-1,-1,-1},
{2,3,11,0,1,8,1,7,8,1,5,7,-1,-1,-1,-1},
{11,2,1,11,1,7,7,1,5,-1,-1,-1,-1,-1,-1,-1},
{9,5,8,8,5,7,10,1,3,10,3,11,-1,-1,-1,-1},
{5,7,0,5,0,9,7,11,0,1,0,10,11,10,0,-1},
{11,10,0,11,0,3,10,5,0,8,0,7,5,7,0,-1},
{11,10,5,7,11,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{10,6,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,5,10,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,0,1,5,10,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,8,3,1,9,8,5,10,6,-1,-1,-1,-1,-1,-1,-1},
{1,6,5,2,6,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,6,5,1,2,6,3,0,8,-1,-1,-1,-1,-1,-1,-1},
{9,6,5,9,0,6,0,2,6,-1,-1,-1,-1,-1,-1,-1},
{5,9,8,5,8,2,5,2,6,3,2,8,-1,-1,-1,-1},
{2,3,11,10,6,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{11,0,8,11,2,0,10,6,5,-1,-1,-1,-1,-1,-1,-1},
{0,1,9,2,3,11,5,10,6,-1,-1,-1,-1,-1,-1,-1},
{5,10,6,1,9,2,9,11,2,9,8,11,-1,-1,-1,-1},
{6,3,11,6,5,3,5,1,3,-1,-1,-1,-1,-1,-1,-1},
{0,8,11,0,11,5,0,5,1,5,11,6,-1,-1,-1,-1},
{3,11,6,0,3,6,0,6,5,0,5,9,-1,-1,-1,-1},
{6,5,9,6,9,11,11,9,8,-1,-1,-1,-1,-1,-1,-1},
{5,10,6,4,7,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,3,0,4,7,3,6,5,10,-1,-1,-1,-1,-1,-1,-1},
{1,9,0,5,10,6,8,4,7,-1,-1,-1,-1,-1,-1,-1},
{10,6,5,1,9,7,1,7,3,7,9,4,-1,-1,-1,-1},
{6,1,2,6,5,1,4,7,8,-1,-1,-1,-1,-1,-1,-1},
{1,2,5,5,2,6,3,0,4,3,4,7,-1,-1,-1,-1},
{8,4,7,9,0,5,0,6,5,0,2,6,-1,-1,-1,-1},
{7,3,9,7,9,4,3,2,9,5,9,6,2,6,9,-1},
{3,11,2,7,8,4,10,6,5,-1,-1,-1,-1,-1,-1,-1},
{5,10,6,4,7,2,4,2,0,2,7,11,-1,-1,-1,-1},
{0,1,9,4,7,8,2,3,11,5,10,6,-1,-1,-1,-1},
{9,2,1,9,11,2,9,4,11,7,11,4,5,10,6,-1},
{8,4,7,3,11,5,3,5,1,5,11,6,-1,-1,-1,-1},
{5,1,11,5,11,6,1,0,11,7,11,4,0,4,11,-1},
{0,5,9,0,6,5,0,3,6,11,6,3,8,4,7,-1},
{6,5,9,6,9,11,4,7,9,7,11,9,-1,-1,-1,-1},
{10,4,9,6,4,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,10,6,4,9,10,0,8,3,-1,-1,-1,-1,-1,-1,-1},
{10,0,1,10,6,0,6,4,0,-1,-1,-1,-1,-1,-1,-1},
{8,1,10,8,10,6,8,3,1,6,4,10,-1,-1,-1,-1},
{1,4,9,1,2,4,2,6,4,-1,-1,-1,-1,-1,-1,-1},
{3,0,8,1,2,9,2,4,9,2,6,4,-1,-1,-1,-1},
{0,2,4,4,2,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{8,3,2,8,2,4,4,2,6,-1,-1,-1,-1,-1,-1,-1},
{10,4,9,10,6,4,11,2,3,-1,-1,-1,-1,-1,-1,-1},
{0,8,2,2,8,11,4,9,10,4,10,6,-1,-1,-1,-1},
{3,11,2,0,1,6,0,6,4,6,1,10,-1,-1,-1,-1},
{6,4,1,6,1,10,4,8,1,2,1,11,8,11,1,-1},
{9,6,4,9,3,6,9,1,3,11,6,3,-1,-1,-1,-1},
{8,11,1,8,1,0,11,6,1,9,1,4,6,4,1,-1},
{3,11,6,3,6,0,0,6,4,-1,-1,-1,-1,-1,-1,-1},
{6,4,8,11,6,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{7,10,6,7,8,10,8,9,10,-1,-1,-1,-1,-1,-1,-1},
{0,7,3,0,10,7,0,9,10,6,7,10,-1,-1,-1,-1},
{10,6,7,1,10,7,1,7,8,1,8,0,-1,-1,-1,-1},
{10,6,7,10,7,1,1,7,3,-1,-1,-1,-1,-1,-1,-1},
{1,2,6,1,6,8,1,8,9,8,6,7,-1,-1,-1,-1},
{2,6,9,2,9,1,6,7,9,0,9,3,7,3,9,-1},
{7,8,0,7,0,6,6,0,2,-1,-1,-1,-1,-1,-1,-1},
{7,3,2,6,7,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{2,3,11,10,6,8,10,8,9,8,6,7,-1,-1,-1,-1},
{2,0,7,2,7,11,0,9,7,6,7,10,9,10,7,-1},
{1,8,0,1,7,8,1,10,7,6,7,10,2,3,11,-1},
{11,2,1,11,1,7,10,6,1,6,7,1,-1,-1,-1,-1},
{8,9,6,8,6,7,9,1,6,11,6,3,1,3,6,-1},
{0,9,1,11,6,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{7,8,0,7,0,6,3,11,0,11,6,0,-1,-1,-1,-1},
{7,11,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{7,6,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,0,8,11,7,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,1,9,11,7,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{8,1,9,8,3,1,11,7,6,-1,-1,-1,-1,-1,-1,-1},
{10,1,2,6,11,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,3,0,8,6,11,7,-1,-1,-1,-1,-1,-1,-1},
{2,9,0,2,10,9,6,11,7,-1,-1,-1,-1,-1,-1,-1},
{6,11,7,2,10,3,10,8,3,10,9,8,-1,-1,-1,-1},
{7,2,3,6,2,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{7,0,8,7,6,0,6,2,0,-1,-1,-1,-1,-1,-1,-1},
{2,7,6,2,3,7,0,1,9,-1,-1,-1,-1,-1,-1,-1},
{1,6,2,1,8,6,1,9,8,8,7,6,-1,-1,-1,-1},
{10,7,6,10,1,7,1,3,7,-1,-1,-1,-1,-1,-1,-1},
{10,7,6,1,7,10,1,8,7,1,0,8,-1,-1,-1,-1},
{0,3,7,0,7,10,0,10,9,6,10,7,-1,-1,-1,-1},
{7,6,10,7,10,8,8,10,9,-1,-1,-1,-1,-1,-1,-1},
{6,8,4,11,8,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,6,11,3,0,6,0,4,6,-1,-1,-1,-1,-1,-1,-1},
{8,6,11,8,4,6,9,0,1,-1,-1,-1,-1,-1,-1,-1},
{9,4,6,9,6,3,9,3,1,11,3,6,-1,-1,-1,-1},
{6,8,4,6,11,8,2,10,1,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,3,0,11,0,6,11,0,4,6,-1,-1,-1,-1},
{4,11,8,4,6,11,0,2,9,2,10,9,-1,-1,-1,-1},
{10,9,3,10,3,2,9,4,3,11,3,6,4,6,3,-1},
{8,2,3,8,4,2,4,6,2,-1,-1,-1,-1,-1,-1,-1},
{0,4,2,4,6,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,9,0,2,3,4,2,4,6,4,3,8,-1,-1,-1,-1},
{1,9,4,1,4,2,2,4,6,-1,-1,-1,-1,-1,-1,-1},
{8,1,3,8,6,1,8,4,6,6,10,1,-1,-1,-1,-1},
{10,1,0,10,0,6,6,0,4,-1,-1,-1,-1,-1,-1,-1},
{4,6,3,4,3,8,6,10,3,0,3,9,10,9,3,-1},
{10,9,4,6,10,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,9,5,7,6,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,4,9,5,11,7,6,-1,-1,-1,-1,-1,-1,-1},
{5,0,1,5,4,0,7,6,11,-1,-1,-1,-1,-1,-1,-1},
{11,7,6,8,3,4,3,5,4,3,1,5,-1,-1,-1,-1},
{9,5,4,10,1,2,7,6,11,-1,-1,-1,-1,-1,-1,-1},
{6,11,7,1,2,10,0,8,3,4,9,5,-1,-1,-1,-1},
{7,6,11,5,4,10,4,2,10,4,0,2,-1,-1,-1,-1},
{3,4,8,3,5,4,3,2,5,10,5,2,11,7,6,-1},
{7,2,3,7,6,2,5,4,9,-1,-1,-1,-1,-1,-1,-1},
{9,5,4,0,8,6,0,6,2,6,8,7,-1,-1,-1,-1},
{3,6,2,3,7,6,1,5,0,5,4,0,-1,-1,-1,-1},
{6,2,8,6,8,7,2,1,8,4,8,5,1,5,8,-1},
{9,5,4,10,1,6,1,7,6,1,3,7,-1,-1,-1,-1},
{1,6,10,1,7,6,1,0,7,8,7,0,9,5,4,-1},
{4,0,10,4,10,5,0,3,10,6,10,7,3,7,10,-1},
{7,6,10,7,10,8,5,4,10,4,8,10,-1,-1,-1,-1},
{6,9,5,6,11,9,11,8,9,-1,-1,-1,-1,-1,-1,-1},
{3,6,11,0,6,3,0,5,6,0,9,5,-1,-1,-1,-1},
{0,11,8,0,5,11,0,1,5,5,6,11,-1,-1,-1,-1},
{6,11,3,6,3,5,5,3,1,-1,-1,-1,-1,-1,-1,-1},
{1,2,10,9,5,11,9,11,8,11,5,6,-1,-1,-1,-1},
{0,11,3,0,6,11,0,9,6,5,6,9,1,2,10,-1},
{11,8,5,11,5,6,8,0,5,10,5,2,0,2,5,-1},
{6,11,3,6,3,5,2,10,3,10,5,3,-1,-1,-1,-1},
{5,8,9,5,2,8,5,6,2,3,8,2,-1,-1,-1,-1},
{9,5,6,9,6,0,0,6,2,-1,-1,-1,-1,-1,-1,-1},
{1,5,8,1,8,0,5,6,8,3,8,2,6,2,8,-1},
{1,5,6,2,1,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,3,6,1,6,10,3,8,6,5,6,9,8,9,6,-1},
{10,1,0,10,0,6,9,5,0,5,6,0,-1,-1,-1,-1},
{0,3,8,5,6,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{10,5,6,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{11,5,10,7,5,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{11,5,10,11,7,5,8,3,0,-1,-1,-1,-1,-1,-1,-1},
{5,11,7,5,10,11,1,9,0,-1,-1,-1,-1,-1,-1,-1},
{10,7,5,10,11,7,9,8,1,8,3,1,-1,-1,-1,-1},
{11,1,2,11,7,1,7,5,1,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,1,2,7,1,7,5,7,2,11,-1,-1,-1,-1},
{9,7,5,9,2,7,9,0,2,2,11,7,-1,-1,-1,-1},
{7,5,2,7,2,11,5,9,2,3,2,8,9,8,2,-1},
{2,5,10,2,3,5,3,7,5,-1,-1,-1,-1,-1,-1,-1},
{8,2,0,8,5,2,8,7,5,10,2,5,-1,-1,-1,-1},
{9,0,1,5,10,3,5,3,7,3,10,2,-1,-1,-1,-1},
{9,8,2,9,2,1,8,7,2,10,2,5,7,5,2,-1},
{1,3,5,3,7,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,8,7,0,7,1,1,7,5,-1,-1,-1,-1,-1,-1,-1},
{9,0,3,9,3,5,5,3,7,-1,-1,-1,-1,-1,-1,-1},
{9,8,7,5,9,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{5,8,4,5,10,8,10,11,8,-1,-1,-1,-1,-1,-1,-1},
{5,0,4,5,11,0,5,10,11,11,3,0,-1,-1,-1,-1},
{0,1,9,8,4,10,8,10,11,10,4,5,-1,-1,-1,-1},
{10,11,4,10,4,5,11,3,4,9,4,1,3,1,4,-1},
{2,5,1,2,8,5,2,11,8,4,5,8,-1,-1,-1,-1},
{0,4,11,0,11,3,4,5,11,2,11,1,5,1,11,-1},
{0,2,5,0,5,9,2,11,5,4,5,8,11,8,5,-1},
{9,4,5,2,11,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{2,5,10,3,5,2,3,4,5,3,8,4,-1,-1,-1,-1},
{5,10,2,5,2,4,4,2,0,-1,-1,-1,-1,-1,-1,-1},
{3,10,2,3,5,10,3,8,5,4,5,8,0,1,9,-1},
{5,10,2,5,2,4,1,9,2,9,4,2,-1,-1,-1,-1},
{8,4,5,8,5,3,3,5,1,-1,-1,-1,-1,-1,-1,-1},
{0,4,5,1,0,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{8,4,5,8,5,3,9,0,5,0,3,5,-1,-1,-1,-1},
{9,4,5,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,11,7,4,9,11,9,10,11,-1,-1,-1,-1,-1,-1,-1},
{0,8,3,4,9,7,9,11,7,9,10,11,-1,-1,-1,-1},
{1,10,11,1,11,4,1,4,0,7,4,11,-1,-1,-1,-1},
{3,1,4,3,4,8,1,10,4,7,4,11,10,11,4,-1},
{4,11,7,9,11,4,9,2,11,9,1,2,-1,-1,-1,-1},
{9,7,4,9,11,7,9,1,11,2,11,1,0,8,3,-1},
{11,7,4,11,4,2,2,4,0,-1,-1,-1,-1,-1,-1,-1},
{11,7,4,11,4,2,8,3,4,3,2,4,-1,-1,-1,-1},
{2,9,10,2,7,9,2,3,7,7,4,9,-1,-1,-1,-1},
{9,10,7,9,7,4,10,2,7,8,7,0,2,0,7,-1},
{3,7,10,3,10,2,7,4,10,1,10,0,4,0,10,-1},
{1,10,2,8,7,4,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,9,1,4,1,7,7,1,3,-1,-1,-1,-1,-1,-1,-1},
{4,9,1,4,1,7,0,8,1,8,7,1,-1,-1,-1,-1},
{4,0,3,7,4,3,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{4,8,7,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{9,10,8,10,11,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,0,9,3,9,11,11,9,10,-1,-1,-1,-1,-1,-1,-1},
{0,1,10,0,10,8,8,10,11,-1,-1,-1,-1,-1,-1,-1},
{3,1,10,11,3,10,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,2,11,1,11,9,9,11,8,-1,-1,-1,-1,-1,-1,-1},
{3,0,9,3,9,11,1,2,9,2,11,9,-1,-1,-1,-1},
{0,2,11,8,0,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{3,2,11,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{2,3,8,2,8,10,10,8,9,-1,-1,-1,-1,-1,-1,-1},
{9,10,2,0,9,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{2,3,8,2,8,10,0,1,8,1,10,8,-1,-1,-1,-1},
{1,10,2,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{1,3,8,9,1,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,9,1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{0,3,8,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1},
{-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1}};

// 立方体12条边对应的顶点对（Bourke 约定）
static const int CUBE_EDGES[12][2] = {
    {0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}
};

static Vec3 lerpVec(const Vec3& a, const Vec3& b, float va, float vb, float iso) {
    float t = (iso - va) / (vb - va);
    if (!(t >= 0.0f && t <= 1.0f)) t = 0.5f; // 数值保护
    return a + (b - a) * t;
}

// 等值面网格：每三角形 9 坐标 + 3 法线（共 12 float/三角形）
struct TriMesh {
    std::vector<float> data;
    size_t ntri = 0;
};

// 对 cube 标量场在 isovalue 处提取等值面 → TriMesh（每三角形 9 坐标 + 3 法线）
// stride>1 时网格降采样（每 stride 个体素取一个角点），控制三角面数量
static void marchCubeSurface(const CubeData& c, float iso, TriMesh& out, int stride = 1) {
    out.data.clear();
    const int nx = c.nx, ny = c.ny, nz = c.nz;
    if (stride < 1) stride = 1;
    const float* v = c.vals.data();
    auto vidx = [&](int ix, int iy, int iz) { return ((size_t)iz * ny + iy) * nx + ix; };

    Vec3 verts[12];
    std::vector<float>& tris = out.data;
    for (int iz = 0; iz + stride < nz; iz += stride) {
        for (int iy = 0; iy + stride < ny; iy += stride) {
            for (int ix = 0; ix + stride < nx; ix += stride) {
                int x1 = ix + stride, y1 = iy + stride, z1 = iz + stride;
                float val[8];
                val[0] = v[vidx(ix,  iy,  iz)];
                val[1] = v[vidx(x1,  iy,  iz)];
                val[2] = v[vidx(x1,  y1,  iz)];
                val[3] = v[vidx(ix,  y1,  iz)];
                val[4] = v[vidx(ix,  iy,  z1)];
                val[5] = v[vidx(x1,  iy,  z1)];
                val[6] = v[vidx(x1,  y1,  z1)];
                val[7] = v[vidx(ix,  y1,  z1)];

                int cubeindex = 0;
                for (int k = 0; k < 8; k++)
                    if (val[k] < iso) cubeindex |= (1 << k);
                if (edgeTable[cubeindex] == 0) continue;

                float x0 = c.ox + ix * c.sx, y0 = c.oy + iy * c.sy, z0 = c.oz + iz * c.sz;
                float dx = x1 * c.sx - ix * c.sx, dy = y1 * c.sy - iy * c.sy, dz = z1 * c.sz - iz * c.sz;
                Vec3 p[8] = {
                    {x0, y0, z0}, {x0 + dx, y0, z0}, {x0 + dx, y0 + dy, z0}, {x0, y0 + dy, z0},
                    {x0, y0, z0 + dz}, {x0 + dx, y0, z0 + dz},
                    {x0 + dx, y0 + dy, z0 + dz}, {x0, y0 + dy, z0 + dz}
                };

                int te = edgeTable[cubeindex];
                for (int e = 0; e < 12; e++) {
                    if (te & (1 << e)) {
                        int a = CUBE_EDGES[e][0], b = CUBE_EDGES[e][1];
                        verts[e] = lerpVec(p[a], p[b], val[a], val[b], iso);
                    }
                }

                const int* tri = triTable[cubeindex];
                for (int t = 0; tri[t] != -1; t += 3) {
                    Vec3 A = verts[tri[t]], B = verts[tri[t + 1]], C = verts[tri[t + 2]];
                    Vec3 n = (B - A).cross(C - A).normalized();
                    tris.push_back(A.x); tris.push_back(A.y); tris.push_back(A.z);
                    tris.push_back(B.x); tris.push_back(B.y); tris.push_back(B.z);
                    tris.push_back(C.x); tris.push_back(C.y); tris.push_back(C.z);
                    tris.push_back(n.x); tris.push_back(n.y); tris.push_back(n.z);
                }
            }
        }
    }
    out.ntri = tris.size() / 12;
}

// ==================== 等值面层（FMO/电子云） ====================
struct MeshLayer {
    std::string path;
    std::string label;
    bool enabled = true;
    bool isFmo = true;          // FMO 层由 B 键切换，密度层由 D 键切换
    bool hasSurface = false;    // 网格加载成功且非空
    float color[3] = {0.15f, 0.35f, 0.95f};
    float alpha = 0.60f;
    GLuint list = 0;            // 显示列表缓存
    TriMesh mesh;
};

static bool loadTri(const std::string& path, TriMesh& m) {
    FILE* f = nullptr;
    if (fopen_s(&f, path.c_str(), "r") != 0 || !f) return false;
    char buf[512];
    std::vector<float> data;
    data.reserve(4096);
    while (fgets(buf, sizeof(buf), f)) {
        if (buf[0] == '#' || buf[0] == '\r' || buf[0] == '\n' || buf[0] == 0) continue;
        float v[12];
        int n = sscanf(buf, "%f%f%f%f%f%f%f%f%f%f%f%f",
                       &v[0], &v[1], &v[2], &v[3], &v[4], &v[5],
                       &v[6], &v[7], &v[8], &v[9], &v[10], &v[11]);
        if (n == 12) for (int k = 0; k < 12; k++) data.push_back(v[k]);
    }
    fclose(f);
    m.data.swap(data);
    m.ntri = m.data.size() / 12;
    return m.ntri > 0;
}

// 按层类型分配标签与配色（与文件名关键词一致的 HOMO/LUMO 蓝红/橙紫、密度青绿）
static void layerStyle(const std::string& tag, bool isNeg, MeshLayer& layer) {
    if (tag == "dens") {
        layer.isFmo = false;
        layer.label = "电子密度";
        layer.color[0] = 0.05f; layer.color[1] = 0.85f; layer.color[2] = 0.68f;
        layer.alpha = 0.32f;
    } else if (tag == "lumo") {
        layer.label = isNeg ? "LUMO(-)" : "LUMO(+)";
        layer.color[0] = isNeg ? 0.66f : 0.95f;
        layer.color[1] = isNeg ? 0.25f : 0.58f;
        layer.color[2] = isNeg ? 0.90f : 0.08f;
    } else { // homo
        layer.label = isNeg ? "HOMO(-)" : "HOMO(+)";
        layer.color[0] = isNeg ? 0.92f : 0.15f;
        layer.color[1] = isNeg ? 0.16f : 0.36f;
        layer.color[2] = isNeg ? 0.16f : 0.95f;
    }
}

// 将网格编译为显示列表（旋转时仅重绘不重建）
static GLuint buildMeshList(const TriMesh& m) {
    if (m.ntri == 0) return 0;
    GLuint list = glGenLists(1);
    glNewList(list, GL_COMPILE);
    glBegin(GL_TRIANGLES);
    for (size_t i = 0; i < m.ntri; i++) {
        const float* d = m.data.data() + i * 12;
        glNormal3f(d[9], d[10], d[11]);
        glVertex3f(d[0], d[1], d[2]);
        glVertex3f(d[3], d[4], d[5]);
        glVertex3f(d[6], d[7], d[8]);
    }
    glEnd();
    glEndList();
    return list;
}

static void rebuildMeshLists(std::vector<MeshLayer>& layers) {
    for (auto& layer : layers) {
        if (layer.list) { glDeleteLists(layer.list, 1); layer.list = 0; }
        if (layer.hasSurface) layer.list = buildMeshList(layer.mesh);
    }
}

static void renderMeshes(const std::vector<MeshLayer>& layers) {
    if (layers.empty()) return;
    glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT | GL_LIGHTING_BIT |
                 GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glDisable(GL_LIGHTING);
    glEnable(GL_DEPTH_TEST);
    glDepthMask(GL_FALSE); // 等值面间不互遮挡，只与分子骨架遮挡
    for (auto& layer : layers) {
        if (!layer.enabled || !layer.list) {
            FILE* dbg = nullptr;
            fopen_s(&dbg, "mesh_dbg.txt", "a");
            if (dbg) {
                fprintf(dbg, "render skip [%s] enabled=%d list=%u\n",
                        layer.label.c_str(), layer.enabled ? 1 : 0, layer.list);
                fclose(dbg);
            }
            continue;
        }
        glColor4f(layer.color[0], layer.color[1], layer.color[2], layer.alpha);
        glCallList(layer.list);
    }
    glDepthMask(GL_TRUE);
    glPopAttrib();
}

// 球棍模型原子球缩放系数：display半径接近共价半径会让相邻球体把键完全遮住
static const float BALL_SCALE = 0.30f;
// 键圆柱半径（球棍模型）
static const float BOND_R = 0.14f;

// 球棍模型原子球半径：不小于键半径的1.55倍，否则小原子（如H）会被键圆柱吞没
static float ballRadius(const ElementInfo& e) {
    return std::max(e.display * BALL_SCALE, BOND_R * 1.55f);
}

// 一次几何测量：2原子=距离(Å)、3原子=键角(°)、4原子=二面角(°)
struct Measurement {
    std::vector<int> atoms;
    double value;
};

struct Viewer {
    Molecule mol;
    std::string filePath;
    bool loaded = false;
    bool showHydrogen = true;
    bool showLabels = false;
    int renderMode = MODE_BALLSTICK;

    Quat rotation;
    float dist = 20.0f;
    Vec3 pan;
    int winW = 1000, winH = 700;

    bool dragging = false, panning = false;
    int lastX = 0, lastY = 0;
    bool animating = false;

    // 拾取与测量
    bool measureMode = false;
    int hoverAtom = -1;
    int downX = 0, downY = 0;              // 区分单击拾取与拖动旋转
    std::vector<int> selAtoms;            // 当前测量选择（≤4个）
    std::vector<Measurement> measures;    // 已提交测量（≤10条）

    bool messageValid = false;
    std::string message;
    DWORD messageUntil = 0;

    // FMO/电子云等值面层（B=开关FMO，D=开关密度），随分子文件同目录自动发现
    std::vector<MeshLayer> layers;
    float meshRadius = 0.0f;   // 等值面网格相对分子中心的最近/最远距离上限（用于视图适配）

    void clearMeasures() {
        selAtoms.clear();
        measures.clear();
        hoverAtom = -1;
    }
    void fitView() {
        dist = std::max(mol.radius, meshRadius) * 2.6f + 2.0f;
        pan = Vec3(0, 0, 0);
    }
    void resetView() {
        rotation = Quat();
        fitView();
    }
    void showMessage(const std::string& msg) {
        message = msg;
        messageValid = true;
        messageUntil = GetTickCount() + 2500;
    }
};

static Viewer g_viewer;
static HWND g_mainHwnd = nullptr; // 临时探测窗口销毁时不触发退出
static bool g_screenshotPending = false; // 本帧渲染完成后导出BMP

// ---- 无头自动连拍（--fmo HOMO→LUMO 各一张） ----
static bool g_fmoAuto = false;            // --fmo：开启自动连拍并退出
static int  g_focusAtom = -1;             // --focus <idx>：聚焦到指定原子（反应位）
static std::string g_outPrefix;           // --out <prefix>：截图前缀（空则用时间戳）
static std::string g_layersSel = "all";   // --layers homo|lumo|none|all
static int  g_frames = 30;                // --frames <n>：每阶段渲染帧数（等待两帧传给渲染器）
static int  g_winW = 0, g_winH = 0;       // --size <WxH>：渲染缓冲分辨率
static bool g_trackMouse = false;        // TrackMouseEvent状态（鼠标离开清悬停）

// ---- 期刊级高清导出（--hi / --px / 按键P） ----
static bool   g_hires = false;           // --hi：启动即高清导出并退出
static bool   g_hiresPending = false;    // 按键P：当前视角导出一次
static std::string g_hiresPrefix;        // --hi <prefix>：高清文件名前缀
static int    g_hiresW = 0, g_hiresH = 0; // --px <WxH>：导出分辨率（默认2600×2600）
static int    g_hiresSS = 3;             // 超采样倍率（3× 抗锯齿）
static std::string g_bondFixFile;        // --bondfix <file>: 成键覆盖文件（add/del 指令）
static std::string g_cameraFile;         // --camera <file>: 镜头方向/取景（quat/pan/dist）

// 从 g_bondFixFile 加载成键覆盖：每行 "add i j order" 或 "del i j"
static void applyBondFixes(Molecule& mol) {
    if (g_bondFixFile.empty()) return;
    FILE* f = nullptr;
    if (fopen_s(&f, g_bondFixFile.c_str(), "r") != 0 || !f) {
        g_viewer.showMessage("bondfix 文件无法打开: " + g_bondFixFile);
        return;
    }
    char line[256];
    int added = 0, removed = 0;
    while (fgets(line, sizeof(line), f)) {
        if (line[0] == '#' || line[0] == '\n' || line[0] == '\r' || line[0] == '\0') continue;
        char cmd[8] = "";
        int i = -1, j = -1, order = 1;
        int nargs = sscanf(line, "%7s %d %d %d", cmd, &i, &j, &order);
        if (nargs < 3) continue;
        if (i < 0 || j < 0 || i >= (int)mol.atoms.size() || j >= (int)mol.atoms.size()) continue;
        if (strcmp(cmd, "add") == 0) {
            bool exists = false;
            for (const Bond& b : mol.bonds) {
                if ((b.a == i && b.b == j) || (b.a == j && b.b == i)) { exists = true; break; }
            }
            if (!exists) {
                mol.bonds.push_back({i, j, std::max(1, std::min(3, order)), false});
                added++;
            }
        } else if (strcmp(cmd, "del") == 0) {
            auto it = mol.bonds.begin();
            while (it != mol.bonds.end()) {
                if ((it->a == i && it->b == j) || (it->a == j && it->b == i))
                    it = mol.bonds.erase(it), removed++;
                else
                    ++it;
            }
        }
    }
    fclose(f);
    if (added || removed) {
        char msg[128];
        sprintf_s(msg, 128, "bondfix: +%d -%d bonds", added, removed);
        g_viewer.showMessage(msg);
    }
}

// 从 g_cameraFile 加载镜头参数：quat w x y z / pan x y / dist d
// 用于把镜头对准反应位点平面正面（由外部脚本计算四元数+平移+距离）
static void applyCameraFile() {
    if (g_cameraFile.empty()) return;
    FILE* f = nullptr;
    if (fopen_s(&f, g_cameraFile.c_str(), "r") != 0 || !f) {
        g_viewer.showMessage("camera 文件无法打开: " + g_cameraFile);
        return;
    }
    char line[256];
    bool gotQ = false, gotP = false, gotD = false;
    while (fgets(line, sizeof(line), f)) {
        if (line[0] == '#' || line[0] == '\n' || line[0] == '\r' || line[0] == '\0') continue;
        char key[16] = "";
        if (sscanf(line, "%15s", key) != 1) continue;
        if (strcmp(key, "quat") == 0) {
            float w = 1, x = 0, y = 0, z = 0;
            if (sscanf(line, "quat %f %f %f %f", &w, &x, &y, &z) == 4) {
                g_viewer.rotation = Quat(w, x, y, z);
                g_viewer.rotation.normalize();
                gotQ = true;
            }
        } else if (strcmp(key, "pan") == 0) {
            float px = 0, py = 0;
            if (sscanf(line, "pan %f %f", &px, &py) == 2) {
                g_viewer.pan = Vec3(px, py, 0);
                gotP = true;
            }
        } else if (strcmp(key, "dist") == 0) {
            float d = 0;
            if (sscanf(line, "dist %f", &d) == 1 && d > 0) {
                g_viewer.dist = std::max(1.0f, std::min(500.0f, d));
                gotD = true;
            }
        }
    }
    fclose(f);
    if (gotQ || gotP || gotD) g_viewer.showMessage("camera: 镜头参数已应用");
}

// 扫描分子文件同目录下的 <basename>_<tag>.{cub,cube,tri} 等值面数据并加载
// tag ∈ homo_pos / homo_neg / lumo_pos / lumo_neg / dens；cube 直接在查看器内做 Marching Cubes
static void loadMeshLayers(const std::string& molPath) {
    for (auto& layer : g_viewer.layers) {
        if (layer.list) { glDeleteLists(layer.list, 1); layer.list = 0; }
    }
    g_viewer.layers.clear();

    std::string dir, base;
    size_t slash = molPath.find_last_of("/\\");
    size_t dot = molPath.find_last_of('.');
    dir = (slash == std::string::npos) ? "." : molPath.substr(0, slash);
    std::string name = (slash == std::string::npos) ? molPath : molPath.substr(slash + 1);
    base = (dot == std::string::npos || dot < slash + 1) ? name : name.substr(0, dot - slash - 1);
    if (base.empty()) return;

    static const char* tags[] = { "homo_pos", "homo_neg", "lumo_pos", "lumo_neg", "dens" };
    static const char* exts[] = { "cub", "cube", "tri" };
    int loaded = 0;
    for (const char* tag : tags) {
        std::string t(tag);
        bool isNeg = t.find("_neg") != std::string::npos;
        std::string baseTag = t.substr(0, t.find('_'));
        for (const char* ext : exts) {
            std::string path = dir + "\\" + base + "_" + tag + "." + ext;
            TriMesh mesh;
            bool ok = false;
            if (strcmp(ext, "tri") == 0) {
                ok = loadTri(path, mesh);
            } else {
                CubeData cube;
                if (parseCube(path, cube)) {
                    float iso = (baseTag == "dens") ? 0.02f : (isNeg ? -0.03f : 0.03f);
                    int stride = (int)std::ceil((float)cube.nx / 70.0f);
                    if (stride < 1) stride = 1;
                    marchCubeSurface(cube, iso, mesh, stride);
                    ok = mesh.ntri > 0;
                }
            }
            if (!ok) continue;
            MeshLayer layer;
            layer.path = path;
            layer.hasSurface = true;
            layer.mesh.data.swap(mesh.data);
            layer.mesh.ntri = mesh.ntri;
            layerStyle(baseTag, isNeg, layer);
            g_viewer.layers.push_back(layer);
            loaded++;
            // 调试：输出网格统计到 mesh_dbg.txt
            {
                FILE* dbg = nullptr;
                fopen_s(&dbg, "mesh_dbg.txt", "a");
                if (dbg) {
                    float mnx = 1e9f, mny = 1e9f, mnz = 1e9f, mxx = -1e9f, mxy = -1e9f, mxz = -1e9f;
                    for (size_t i = 0; i < layer.mesh.ntri; i++) {
                        for (int k = 0; k < 3; k++) {
                            float x = layer.mesh.data[i * 12 + k * 3];
                            float y = layer.mesh.data[i * 12 + k * 3 + 1];
                            float z = layer.mesh.data[i * 12 + k * 3 + 2];
                            if (x < mnx) mnx = x; if (x > mxx) mxx = x;
                            if (y < mny) mny = y; if (y > mxy) mxy = y;
                            if (z < mnz) mnz = z; if (z > mxz) mxz = z;
                        }
                    }
                    fprintf(dbg, "[%s] ntri=%zu bbox=(%.2f,%.2f,%.2f)-(%.2f,%.2f,%.2f) alpha=%.2f color=(%.2f,%.2f,%.2f)\n",
                            layer.label.c_str(), layer.mesh.ntri, mnx, mny, mnz, mxx, mxy, mxz,
                            layer.alpha, layer.color[0], layer.color[1], layer.color[2]);
                    fclose(dbg);
                }
            }
            break; // 同一 tag 只取第一个可用扩展名
        }
    }
    rebuildMeshLists(g_viewer.layers);
    // 计算等值面相对分子中心的最大半径（视图适配用）
    g_viewer.meshRadius = 0.0f;
    {
        const Vec3 c = g_viewer.mol.center;
        float r2 = 0.0f;
        for (auto& L : g_viewer.layers) {
            const TriMesh& m = L.mesh;
            for (size_t i = 0; i < m.ntri; i++) {
                for (int k = 0; k < 3; k++) {
                    float dx = m.data[i * 12 + k * 3] - c.x;
                    float dy = m.data[i * 12 + k * 3 + 1] - c.y;
                    float dz = m.data[i * 12 + k * 3 + 2] - c.z;
                    float d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r2) r2 = d2;
                }
            }
        }
        g_viewer.meshRadius = std::sqrt(r2);
    }
    if (loaded > 0) {
        char msg[128];
        sprintf_s(msg, 128, "已加载 %d 个等值面层（B=开关FMO，D=开关密度）", loaded);
        g_viewer.showMessage(msg);
    }
}

// ==================== 文本渲染（Unicode位图字体） ====================
static std::unordered_map<wchar_t, GLuint> g_fontLists;
static GLuint g_fontBase = 0;
static bool g_fontReady = false;
static HFONT g_font = nullptr;

static void buildFonts(HDC hdc) {
    if (g_fontReady) { wglMakeCurrent(hdc, wglGetCurrentContext()); return; }
    g_font = CreateFontW(-14, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE,
                         DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS,
                         ANTIALIASED_QUALITY, DEFAULT_PITCH | FF_DONTCARE, L"微软雅黑");
    HFONT old = (HFONT)SelectObject(hdc, g_font);
    // ASCII范围一次性生成
    g_fontBase = glGenLists(256);
    wglUseFontBitmapsW(hdc, 0x20, 224, g_fontBase); // 0x20-0xFF
    SelectObject(hdc, old);
    g_fontReady = true;
}

// 为单个CJK字符生成显示列表（懒加载）
static GLuint glyphFor(wchar_t c, HDC hdc) {
    auto it = g_fontLists.find(c);
    if (it != g_fontLists.end()) return it->second;
    HFONT old = (HFONT)SelectObject(hdc, g_font);
    GLuint list = glGenLists(1);
    BOOL ok = wglUseFontBitmapsW(hdc, c, 1, list);
    SelectObject(hdc, old);
    if (!ok) { glDeleteLists(list, 1); list = 0; }
    g_fontLists[c] = list;
    return list;
}

static void drawTextW(HDC hdc, float x, float y, const wchar_t* text) {
    glRasterPos2f(x, y);
    for (const wchar_t* p = text; *p; p++) {
        wchar_t c = *p;
        if (c < 256) {
            GLuint list = g_fontBase + c - 0x20;
            glCallList(list);
        } else {
            GLuint list = glyphFor(c, hdc);
            if (list) glCallList(list);
        }
    }
}

// ==================== 渲染 ====================
static GLUquadric* g_quad = nullptr;

static void drawSphere(const Vec3& p, float r, float cr, float cg, float cb) {
    glPushMatrix();
    glTranslatef(p.x, p.y, p.z);
    glColor3f(cr, cg, cb);
    gluSphere(g_quad, r, 24, 16);
    glPopMatrix();
}

static void drawCylinder(const Vec3& a, const Vec3& b, float r,
                         float cr, float cg, float cb) {
    // 键颜色向浅灰混合，避免深色原子（如碳）的键与球体混在一起
    cr = cr * 0.55f + 0.42f; cg = cg * 0.55f + 0.42f; cb = cb * 0.55f + 0.42f;
    Vec3 d = b - a;
    float len = d.len();
    if (len < 1e-6f) return;
    d = d * (1.0f / len);
    // gluCylinder沿+Z伸展：旋转参考轴必须是Z，把Z轴转到键方向d
    Vec3 zAxis(0, 0, 1);
    Vec3 axis = zAxis.cross(d);
    float cosA = std::max(-1.0f, std::min(1.0f, zAxis.dot(d)));
    float angle = (float)(std::acos(cosA) * 180.0 / 3.14159265);
    glPushMatrix();
    glTranslatef(a.x, a.y, a.z);
    if (axis.len() > 1e-5f)
        glRotatef(angle, axis.x, axis.y, axis.z);
    else if (d.z < 0.0f)
        glRotatef(180.0f, 1.0f, 0.0f, 0.0f); // d≈−Z时绕X轴翻转180°
    glColor3f(cr, cg, cb);
    gluCylinder(g_quad, r, r, len, 16, 4);
    glPopMatrix();
}

static bool isHydrogen(int idx) {
    return g_viewer.mol.atoms[idx].symbol == "H";
}

static bool atomVisible(int idx) {
    return g_viewer.showHydrogen || !isHydrogen(idx);
}

// ==================== 原子拾取与几何测量 ====================
// 屏幕标签（3D阶段投影到屏幕，叠加层统一绘制）
struct ScreenLabel { float x, y; wchar_t text[64]; };
static std::vector<ScreenLabel> g_pendingLabels;

static double atomDistance(const Molecule& mol, int i, int j) {
    return (mol.atoms[i].pos - mol.atoms[j].pos).len();
}

static double atomAngle(const Molecule& mol, int i, int j, int k) { // 顶点在j
    Vec3 a = mol.atoms[i].pos - mol.atoms[j].pos;
    Vec3 b = mol.atoms[k].pos - mol.atoms[j].pos;
    float d = a.normalized().dot(b.normalized());
    d = std::max(-1.0f, std::min(1.0f, d));
    return std::acos(d) * 180.0 / 3.14159265358979;
}

static double atomDihedral(const Molecule& mol, int i, int j, int k, int l) {
    Vec3 b1 = mol.atoms[j].pos - mol.atoms[i].pos;
    Vec3 b2 = mol.atoms[k].pos - mol.atoms[j].pos;
    Vec3 b3 = mol.atoms[l].pos - mol.atoms[k].pos;
    Vec3 n1 = b1.cross(b2);
    Vec3 n2 = b2.cross(b3);
    float lb2 = b2.len();
    if (lb2 < 1e-6f) return 0.0;
    Vec3 m1 = n1.cross(b2 * (1.0f / lb2));
    return std::atan2((double)m1.dot(n2), (double)n1.dot(n2)) * 180.0 / 3.14159265358979;
}

// 鼠标射线拾取：屏幕坐标→眼空间射线→逆变换到世界→最近球体求交
static bool pickAtom(int mx, int my, int& picked) {
    const Viewer& v = g_viewer;
    const Molecule& mol = v.mol;
    if (mol.atoms.empty()) return false;
    float aspect = (float)v.winW / (float)(v.winH > 0 ? v.winH : 1);
    float tanHalf = std::tan(20.0f * 3.14159265f / 180.0f); // fov=40°
    float ndcX = 2.0f * mx / (float)v.winW - 1.0f;
    float ndcY = 1.0f - 2.0f * my / (float)v.winH;
    Vec3 dirEye(ndcX * tanHalf * aspect, ndcY * tanHalf, -1.0f);
    dirEye = dirEye.normalized();
    // 模型视图: p_eye = R·(p_w−c) + pan + (0,0,−dist) → 逆: p_w = Rᵀ·(p_eye−pan−(0,0,−dist)) + c
    Quat inv = v.rotation.conjugate();
    Vec3 origin = inv.rotateVec(Vec3(-v.pan.x, -v.pan.y, v.dist)) + mol.center;
    Vec3 dir = inv.rotateVec(dirEye);
    int best = -1;
    float bestT = 1e30f;
    for (size_t i = 0; i < mol.atoms.size(); i++) {
        if (!atomVisible((int)i)) continue;
        const Atom& a = mol.atoms[i];
        const ElementInfo& e = elemInfo(a.symbol);
        float r = (v.renderMode == MODE_SPACEFILL) ? e.vdw : ballRadius(e);
        r = r * 1.4f + 0.06f; // 拾取容差
        Vec3 oc = origin - a.pos;
        float b = oc.dot(dir);
        float c2 = oc.dot(oc) - r * r;
        float disc = b * b - c2;
        if (disc < 0.0f) continue;
        float sq = std::sqrt(disc);
        float t = -b - sq;
        if (t < 0.0f) t = -b + sq; // 相机在球内时取另一交点
        if (t < 0.0f || t >= bestT) continue;
        bestT = t;
        best = (int)i;
    }
    if (best < 0) return false;
    picked = best;
    return true;
}

static void projectToScreen(const Vec3& p, float& sx, float& sy) {
    double mv[16], pj[16];
    GLint vp[4];
    glGetDoublev(GL_MODELVIEW_MATRIX, mv);
    glGetDoublev(GL_PROJECTION_MATRIX, pj);
    glGetIntegerv(GL_VIEWPORT, vp);
    double wx = 0, wy = 0, wz = 0;
    if (gluProject(p.x, p.y, p.z, mv, pj, vp, &wx, &wy, &wz) == GL_TRUE) {
        sx = (float)wx;
        sy = (float)wy;
    } else {
        sx = -1.0f;
        sy = -1.0f;
    }
}

// 原子外圈线框标记（悬停=蓝，选中=橙）
static void drawAtomMarker(int idx, float cr, float cg, float cb) {
    const Atom& a = g_viewer.mol.atoms[idx];
    const ElementInfo& e = elemInfo(a.symbol);
    float r = ((g_viewer.renderMode == MODE_SPACEFILL) ? e.vdw : ballRadius(e)) * 1.5f + 0.05f;
    glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
    glDisable(GL_LIGHTING);
    gluQuadricDrawStyle(g_quad, GLU_LINE);
    glPushMatrix();
    glTranslatef(a.pos.x, a.pos.y, a.pos.z);
    glColor3f(cr, cg, cb);
    gluSphere(g_quad, r, 20, 12);
    glPopMatrix();
    gluQuadricDrawStyle(g_quad, GLU_FILL);
    glPopAttrib();
}

static void drawMeasureChain(const std::vector<int>& atoms) {
    if (atoms.size() < 2) return;
    glBegin(GL_LINES);
    for (size_t i = 0; i + 1 < atoms.size(); i++) {
        const Vec3& a = g_viewer.mol.atoms[atoms[i]].pos;
        const Vec3& b = g_viewer.mol.atoms[atoms[i + 1]].pos;
        glVertex3f(a.x, a.y, a.z);
        glVertex3f(b.x, b.y, b.z);
    }
    glEnd();
}

static void pushLabel(const Vec3& anchor, const wchar_t* text) {
    float sx, sy;
    projectToScreen(anchor, sx, sy);
    if (sx < 0) return;
    ScreenLabel lbl;
    lbl.x = sx + 4.0f;
    lbl.y = sy + 4.0f;
    swprintf(lbl.text, 64, L"%s", text);
    g_pendingLabels.push_back(lbl);
}

static void commitMeasureSelection() {
    auto& s = g_viewer.selAtoms;
    const Molecule& mol = g_viewer.mol;
    if (s.size() < 2) return;
    double val = 0;
    if (s.size() == 2)      val = atomDistance(mol, s[0], s[1]);
    else if (s.size() == 3) val = atomAngle(mol, s[0], s[1], s[2]);
    else                    val = atomDihedral(mol, s[0], s[1], s[2], s[3]);
    g_viewer.measures.push_back({s, val});
    if (g_viewer.measures.size() > 10)
        g_viewer.measures.erase(g_viewer.measures.begin());
    char msg[128];
    const char* kind = (s.size() == 2) ? "距离" : (s.size() == 3) ? "键角" : "二面角";
    const char* unit = (s.size() == 2) ? " Å" : "°";
    sprintf_s(msg, 128, "已提交%s: %.3f%s", kind, val, unit);
    s.clear();
    g_viewer.showMessage(msg);
}

// 测量渲染：悬停/选中标记 + 已提交测量虚线 + 实时数值标签
static void renderMeasurements() {
    g_pendingLabels.clear();
    if (!g_viewer.loaded) return;
    const Molecule& mol = g_viewer.mol;
    int na = (int)mol.atoms.size();

    if (g_viewer.hoverAtom >= 0 && g_viewer.hoverAtom < na && atomVisible(g_viewer.hoverAtom))
        drawAtomMarker(g_viewer.hoverAtom, 0.20f, 0.55f, 0.95f);
    for (int idx : g_viewer.selAtoms)
        if (idx >= 0 && idx < na) drawAtomMarker(idx, 1.0f, 0.55f, 0.05f);

    if (!g_viewer.measureMode && g_viewer.measures.empty()) return;

    glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT | GL_LINE_BIT);
    glDisable(GL_LIGHTING);
    glDisable(GL_DEPTH_TEST); // 虚线始终可见，不被球体遮挡
    glLineWidth(2.0f);
    glEnable(GL_LINE_STIPPLE);
    wchar_t buf[64];

    // 已提交测量：暗橙虚线 + 数值标签
    glLineStipple(1, 0x0F0F);
    glColor3f(0.80f, 0.42f, 0.05f);
    for (const auto& m : g_viewer.measures) {
        drawMeasureChain(m.atoms);
        Vec3 anchor = mol.atoms[m.atoms[m.atoms.size() / 2]].pos;
        if (m.atoms.size() == 2)
            anchor = (mol.atoms[m.atoms[0]].pos + mol.atoms[m.atoms[1]].pos) * 0.5f;
        if (m.atoms.size() == 2)
            swprintf(buf, 64, L"%d-%d: %.3f Å", m.atoms[0] + 1, m.atoms[1] + 1, m.value);
        else if (m.atoms.size() == 3)
            swprintf(buf, 64, L"%d-%d-%d: %.1f°", m.atoms[0] + 1, m.atoms[1] + 1, m.atoms[2] + 1, m.value);
        else
            swprintf(buf, 64, L"%d-%d-%d-%d: %.1f°", m.atoms[0] + 1, m.atoms[1] + 1, m.atoms[2] + 1, m.atoms[3] + 1, m.value);
        pushLabel(anchor, buf);
    }

    // 当前选择：亮橙虚线 + 实时数值
    glLineStipple(1, 0x00FF);
    glColor3f(1.0f, 0.60f, 0.10f);
    const std::vector<int>& s = g_viewer.selAtoms;
    drawMeasureChain(s);
    if (s.size() == 2) {
        Vec3 anchor = (mol.atoms[s[0]].pos + mol.atoms[s[1]].pos) * 0.5f;
        swprintf(buf, 64, L"%d-%d: %.3f Å", s[0] + 1, s[1] + 1, atomDistance(mol, s[0], s[1]));
        pushLabel(anchor, buf);
    } else if (s.size() == 3) {
        Vec3 anchor = mol.atoms[s[1]].pos; // 顶点原子
        swprintf(buf, 64, L"%d-%d-%d: %.1f°", s[0] + 1, s[1] + 1, s[2] + 1,
                 atomAngle(mol, s[0], s[1], s[2]));
        pushLabel(anchor, buf);
    }

    glPopAttrib();
}

static void renderMolecule() {
    const Molecule& mol = g_viewer.mol;
    const float bondR = BOND_R;

    // ---- 键（在原子之前画，原子球体覆盖端部）----
    if (g_viewer.renderMode != MODE_SPACEFILL) {
        // 相机在模型空间的朝向：多键偏移方向需垂直于键且垂直于视线（落在屏幕平面内）
        Vec3 camDir = g_viewer.rotation.conjugate().rotateVec(Vec3(0, 0, 1));
        for (const Bond& bond : mol.bonds) {
            if (!atomVisible(bond.a) || !atomVisible(bond.b)) continue;
            const Atom& A = mol.atoms[bond.a];
            const Atom& B = mol.atoms[bond.b];
            const ElementInfo& ea = elemInfo(A.symbol);
            const ElementInfo& eb = elemInfo(B.symbol);
            Vec3 mid = (A.pos + B.pos) * 0.5f;
            if (g_viewer.renderMode == MODE_WIRE) {
                glLineWidth(1.5f);
                glBegin(GL_LINES);
                glColor3f(0.55f, 0.55f, 0.60f);
                glVertex3f(A.pos.x, A.pos.y, A.pos.z);
                glVertex3f(B.pos.x, B.pos.y, B.pos.z);
                glEnd();
                continue;
            }
            if (bond.order <= 1) {
                // 各画一半，颜色分属两端原子
                drawCylinder(A.pos, mid, bondR, ea.r, ea.g, ea.b);
                drawCylinder(mid, B.pos, bondR, eb.r, eb.g, eb.b);
            } else {
                // 双/三键：平行偏移圆柱，偏移方向垂直于键且在屏幕平面内（否则面对视线时会重叠成单键）
                Vec3 dir = (B.pos - A.pos).normalized();
                Vec3 perpDir = dir.cross(camDir);
                if (perpDir.len() < 1e-4f) perpDir = dir.cross(Vec3(0, 1, 0));
                if (perpDir.len() < 1e-4f) perpDir = dir.cross(Vec3(1, 0, 0));
                perpDir = perpDir.normalized();
                int n = bond.order;
                float rodR = bondR * 0.72f;
                // 偏移限制在原子球内，避免圆柱端点悬在球外
                float ballMin = std::min(ballRadius(ea), ballRadius(eb));
                float maxOff = ballMin - rodR;
                if (maxOff < 0.03f) maxOff = 0.03f;
                float spacing = 2.0f * maxOff / (float)(n - 1);
                float offBase = -(n - 1) * 0.5f;
                for (int k = 0; k < n; k++) {
                    Vec3 o = perpDir * (spacing * (offBase + k));
                    Vec3 a2 = A.pos + o, m2 = mid + o, b2 = B.pos + o;
                    drawCylinder(a2, m2, rodR, ea.r, ea.g, ea.b);
                    drawCylinder(m2, b2, rodR, eb.r, eb.g, eb.b);
                }
            }
        }
    }

    // ---- 原子 ----
    if (g_viewer.renderMode != MODE_WIRE) {
        for (size_t i = 0; i < mol.atoms.size(); i++) {
            if (!atomVisible((int)i)) continue;
            const Atom& a = mol.atoms[i];
            const ElementInfo& e = elemInfo(a.symbol);
            float r = (g_viewer.renderMode == MODE_SPACEFILL) ? e.vdw : ballRadius(e);
            drawSphere(a.pos, r, e.r, e.g, e.b);
        }
    }
}

static void renderLabels() {
    // 原子符号标签（始终面向屏幕）
    const Molecule& mol = g_viewer.mol;
    glDisable(GL_LIGHTING);
    glColor3f(0.15f, 0.15f, 0.20f);
    for (size_t i = 0; i < mol.atoms.size(); i++) {
        if (!atomVisible((int)i)) continue;
        const Atom& a = mol.atoms[i];
        const ElementInfo& e = elemInfo(a.symbol);
        float r = (g_viewer.renderMode == MODE_SPACEFILL) ? e.vdw : ballRadius(e);
        // 标签放在原子正上方，用窗口坐标投影
        double mv[16], pj[16];
        glGetDoublev(GL_MODELVIEW_MATRIX, mv);
        glGetDoublev(GL_PROJECTION_MATRIX, pj);
        GLint vp[4];
        glGetIntegerv(GL_VIEWPORT, vp);
        double wx, wy, wz;
        float lift = r + 0.35f;
        Vec3 p = a.pos + Vec3(0, lift, 0);
        if (gluProject(p.x, p.y, p.z, mv, pj, vp, &wx, &wy, &wz) == GL_TRUE) {
            if (wz < 1.0) { // 在裁剪范围内
                // 切换到2D
                glMatrixMode(GL_PROJECTION); glPushMatrix();
                glMatrixMode(GL_MODELVIEW); glPushMatrix();
                glPushAttrib(GL_TRANSFORM_BIT);
                glLoadIdentity();
                glMatrixMode(GL_PROJECTION); glLoadIdentity();
                gluOrtho2D(0, g_viewer.winW, 0, g_viewer.winH);
                glRasterPos2f((float)wx + 2.0f, (float)wy + 2.0f);
                const std::string& sym = a.symbol;
                glPushAttrib(GL_CURRENT_BIT);
                glColor3f(0.10f, 0.10f, 0.15f);
                glPopAttrib();
                glRasterPos2f((float)wx + 2.0f, (float)wy + 2.0f);
                for (char c : sym) {
                    if (c >= 0x20) glCallList(g_fontBase + c - 0x20);
                }
                glPopAttrib();
                glMatrixMode(GL_PROJECTION); glPopMatrix();
                glMatrixMode(GL_MODELVIEW); glPopMatrix();
            }
        }
    }
    glEnable(GL_LIGHTING);
}

// ==================== 背景渐变 ====================
// 顶部亮、底部微暗的冷色垂直渐变（3D场景之前绘制）
static void drawBackgroundGradient() {
    glPushAttrib(GL_ENABLE_BIT | GL_TRANSFORM_BIT);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_LIGHTING);
    glMatrixMode(GL_PROJECTION); glPushMatrix();
    glMatrixMode(GL_MODELVIEW); glPushMatrix();
    glLoadIdentity();
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    gluOrtho2D(0, (double)g_viewer.winW, 0, (double)g_viewer.winH);
    glBegin(GL_QUADS);
    glColor3f(0.972f, 0.980f, 0.988f); // 顶部
    glVertex2f(0.0f, (float)g_viewer.winH);
    glVertex2f((float)g_viewer.winW, (float)g_viewer.winH);
    glColor3f(0.858f, 0.886f, 0.922f); // 底部
    glVertex2f((float)g_viewer.winW, 0.0f);
    glVertex2f(0.0f, 0.0f);
    glEnd();
    glMatrixMode(GL_PROJECTION); glPopMatrix();
    glMatrixMode(GL_MODELVIEW); glPopMatrix();
    glPopAttrib();
}

// ==================== BMP截图导出 ====================
// 读取当前后台缓冲区，保存为24位BMP（行4字节对齐，BGR，自底向上）
static void saveScreenshot(const std::string& nameOverride = "") {
    int w = g_viewer.winW, h = g_viewer.winH;
    if (w <= 0 || h <= 0) return;
    int srcRow = w * 3;
    int dstRow = (srcRow + 3) & ~3;
    std::vector<unsigned char> src((size_t)srcRow * h);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadPixels(0, 0, w, h, GL_RGB, GL_UNSIGNED_BYTE, src.data());
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
    std::vector<unsigned char> dst((size_t)dstRow * h, 0);
    for (int y = 0; y < h; y++) {
        const unsigned char* s = src.data() + (size_t)y * srcRow;
        unsigned char* d = dst.data() + (size_t)y * dstRow;
        for (int x = 0; x < w; x++) {
            d[x * 3]     = s[x * 3 + 2]; // RGB→BGR
            d[x * 3 + 1] = s[x * 3 + 1];
            d[x * 3 + 2] = s[x * 3];
        }
    }

    std::string fname;
    if (!nameOverride.empty()) {
        fname = nameOverride;
        if (fname.size() < 4 || fname.substr(fname.size() - 4) != ".bmp") fname += ".bmp";
    } else {
        SYSTEMTIME st;
        GetLocalTime(&st);
        wchar_t wname[64];
        swprintf(wname, 64, L"molview_%04d%02d%02d_%02d%02d%02d.bmp",
                 st.wYear, st.wMonth, st.wDay, st.wHour, st.wMinute, st.wSecond);
        char nm[128];
        WideCharToMultiByte(CP_UTF8, 0, wname, -1, nm, 128, nullptr, nullptr);
        fname = nm;
    }
    const char* name = fname.c_str();

#pragma pack(push, 1)
    struct { char type[2]; DWORD size; WORD r1, r2; DWORD off; } fh;
    struct { DWORD size; LONG w, h; WORD planes, bpp; DWORD comp, imgSize;
             LONG xppm, yppm; DWORD clrUsed, clrImp; } ih;
#pragma pack(pop)
    DWORD dataSize = (DWORD)(dstRow * h);
    fh.type[0] = 'B'; fh.type[1] = 'M';
    fh.size = 14 + 40 + dataSize;
    fh.r1 = 0; fh.r2 = 0;
    fh.off = 54;
    ih.size = 40; ih.w = w; ih.h = h; ih.planes = 1; ih.bpp = 24;
    ih.comp = 0; ih.imgSize = dataSize; ih.xppm = 2835; ih.yppm = 2835;
    ih.clrUsed = 0; ih.clrImp = 0;

    FILE* f = nullptr;
    if (fopen_s(&f, name, "wb") == 0 && f) {
        fwrite(&fh, sizeof(fh), 1, f);
        fwrite(&ih, sizeof(ih), 1, f);
        fwrite(dst.data(), 1, dst.size(), f);
        fclose(f);
        g_viewer.showMessage(std::string("截图已保存: ") + name);
    } else {
        g_viewer.showMessage(std::string("截图保存失败: ") + name);
    }
}

// ==================== HUD/图例 ====================
// ==================== 期刊级高清导出 ====================
// 用隐藏离屏窗口以 g_hiresW×g_hiresSS 超采样渲染当前视角，均值降采样抗锯齿，
// 输出 <prefix>.png（白底球棍）与 <prefix>_t.png（透明背景）。
// 离屏渲染只画分子几何(renderMolecule)，不含HUD/测量/等值面，保持出版级干净。
static void renderSceneBuffer(int W, int H, float bgR, float bgG, float bgB) {
    glViewport(0, 0, W, H);
    glClearColor(bgR, bgG, bgB, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    gluPerspective(40.0, (double)W / (double)H, 0.1, 2000.0);
    glMatrixMode(GL_MODELVIEW); glLoadIdentity();
    GLfloat lp[] = { 0.4f, 0.5f, 1.0f, 0.0f };
    GLfloat la[] = { 0.38f, 0.38f, 0.38f, 1.0f };
    GLfloat ld[] = { 0.75f, 0.75f, 0.75f, 1.0f };
    glLightfv(GL_LIGHT0, GL_POSITION, lp);
    glLightfv(GL_LIGHT0, GL_AMBIENT, la);
    glLightfv(GL_LIGHT0, GL_DIFFUSE, ld);
    gluLookAt(0, 0, g_viewer.dist, 0, 0, 0, 0, 1, 0);
    glTranslatef(g_viewer.pan.x, g_viewer.pan.y, 0);
    float rm[16];
    g_viewer.rotation.toMatrix(rm);
    glMultMatrixf(rm);
    glTranslatef(-g_viewer.mol.center.x, -g_viewer.mol.center.y, -g_viewer.mol.center.z);
    if (g_viewer.loaded) renderMolecule();
}

// 用 GDI+ 把 top-down BGRA 直通(straight alpha)缓冲保存为 PNG
static void savePngBgra(const std::vector<unsigned char>& bgra, int W, int H,
                        const std::string& path, Gdiplus::GdiplusStartupInput& gsiInput,
                        ULONG_PTR& gdiToken, bool first) {
    if (first) Gdiplus::GdiplusStartup(&gdiToken, &gsiInput, nullptr);
    CLSID pngClsid;
    CLSIDFromString(L"{557CF406-1A04-11D3-9A73-0000F81EF32E}", &pngClsid);
    int nw = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, nullptr, 0);
    std::wstring wp((size_t)nw, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, &wp[0], nw);

    // 显式分配 Bitmap 并用 LockBits 拷贝，避免 scan-pointer 构造在某些显存/内存压力下的间歇性失败
    for (int attempt = 0; attempt < 2; attempt++) {
        Gdiplus::Bitmap bmp(W, H, PixelFormat32bppARGB);
        Gdiplus::Rect r(0, 0, W, H);
        Gdiplus::BitmapData bd;
        if (bmp.LockBits(&r, Gdiplus::ImageLockModeWrite, PixelFormat32bppARGB, &bd) == Gdiplus::Ok) {
            unsigned char* dst = (unsigned char*)bd.Scan0;
            for (int y = 0; y < H; y++)
                memcpy(dst + (size_t)y * bd.Stride, bgra.data() + (size_t)y * W * 4, (size_t)W * 4);
            bmp.UnlockBits(&bd);
        }
        Gdiplus::Status st = bmp.Save(wp.c_str(), &pngClsid, nullptr);
        if (st == Gdiplus::Ok) return;
    }
}

static void renderHiRes() {
    if (!g_viewer.loaded || g_viewer.mol.atoms.empty()) {
        g_viewer.showMessage("高清导出：请先加载分子");
        return;
    }
    std::string prefix = g_hiresPrefix;
    if (prefix.empty()) prefix = g_outPrefix.empty() ? "hires" : g_outPrefix;
    int Hw = g_hiresW, Hh = g_hiresH, SS = g_hiresSS;
    if (Hw <= 0 || Hh <= 0) { Hw = 2600; Hh = 2600; }
    if (SS < 2) SS = 2;
    int W2 = std::min(9000, Hw * SS), H2 = std::min(9000, Hh * SS);

    // 隐藏离屏窗口 + 独立上下文（复用已注册类）。窗口尺寸须等于渲染缓冲，
    // 否则默认帧缓冲只有1×1，glReadPixels 会在窗口范围外返回全0（黑帧）。
    HWND hv = CreateWindowExW(0, L"ZhisuanMolView", L"hires", WS_POPUP,
                              0, 0, W2, H2, nullptr, nullptr, GetModuleHandleW(nullptr), nullptr);
    if (!hv) { g_viewer.showMessage("高清导出：离屏窗口创建失败"); return; }
    HDC hdc = GetDC(hv);
    PIXELFORMATDESCRIPTOR pfd = { sizeof(pfd) };
    pfd.nVersion = 1;
    pfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
    pfd.iPixelType = PFD_TYPE_RGBA;
    pfd.cColorBits = 24; pfd.cDepthBits = 24;
    pfd.iLayerType = PFD_MAIN_PLANE;
    int pf = ChoosePixelFormat(hdc, &pfd);
    if (pf <= 0 || !SetPixelFormat(hdc, pf, &pfd)) {
        ReleaseDC(hv, hdc); DestroyWindow(hv);
        g_viewer.showMessage("高清导出：像素格式失败");
        return;
    }
    HGLRC rc = wglCreateContext(hdc);
    if (!rc) { ReleaseDC(hv, hdc); DestroyWindow(hv); g_viewer.showMessage("高清导出：GL上下文失败"); return; }
    HGLRC old = wglGetCurrentContext();
    HDC   oldDC = wglGetCurrentDC();
    wglMakeCurrent(hdc, rc);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_LIGHTING); glEnable(GL_LIGHT0);
    glEnable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    glEnable(GL_NORMALIZE);
    { GLfloat spec[]={0.45f,0.45f,0.45f,1.0f}, msp[]={0.35f,0.35f,0.35f,1.0f};
      glLightfv(GL_LIGHT0,GL_SPECULAR,spec); glMaterialfv(GL_FRONT_AND_BACK,GL_SPECULAR,msp);
      glMaterialf(GL_FRONT_AND_BACK,GL_SHININESS,40.0f); }

    // 白底帧 → 超采样颜色
    renderSceneBuffer(W2, H2, 1.0f, 1.0f, 1.0f);
    std::vector<unsigned char> over(W2 * H2 * 4);
    glReadBuffer(GL_BACK);
    glFlush();
    glReadPixels(0, 0, W2, H2, GL_RGBA, GL_UNSIGNED_BYTE, over.data());

    // 黑底帧 → 前景覆盖 mask（几何所在处 brightness>0）
    renderSceneBuffer(W2, H2, 0.0f, 0.0f, 0.0f);
    std::vector<unsigned char> mask(W2 * H2 * 4);
    glReadPixels(0, 0, W2, H2, GL_RGBA, GL_UNSIGNED_BYTE, mask.data());

    wglMakeCurrent(oldDC, old);
    wglDeleteContext(rc);
    ReleaseDC(hv, hdc);
    DestroyWindow(hv);

    // 2x2 均值降采样 → 白底图 + 透明图（BA 顺序给 GDI+）
    std::vector<unsigned char> whiteBuf((size_t)Hw * Hh * 4, 255);
    std::vector<unsigned char> alphaBuf((size_t)Hw * Hh * 4, 0);
    for (int y = 0; y < Hh; y++) {
        for (int x = 0; x < Hw; x++) {
            int r = 0, g = 0, b = 0, cov = 0;   // cov=覆盖的子像素数
            for (int dy = 0; dy < SS; dy++)
                for (int dx = 0; dx < SS; dx++) {
                    int sx = x * SS + dx, sy = y * SS + dy;
                    size_t o = ((size_t)sy * W2 + sx) * 4;
                    r += over[o]; g += over[o + 1]; b += over[o + 2];
                    size_t m = ((size_t)sy * W2 + sx) * 4;
                    float lum = (mask[m] + mask[m + 1] + mask[m + 2]) / 3.0f;
                    if (lum > 0.02f) cov++;
                }
            int n = SS * SS;
            size_t d = ((size_t)y * Hw + x) * 4;
            whiteBuf[d]     = (unsigned char)(b / n); // BGR→GDI+
            whiteBuf[d + 1] = (unsigned char)(g / n);
            whiteBuf[d + 2] = (unsigned char)(r / n);
            whiteBuf[d + 3] = 255;
            alphaBuf[d]     = (unsigned char)(b / n);
            alphaBuf[d + 1] = (unsigned char)(g / n);
            alphaBuf[d + 2] = (unsigned char)(r / n);
            alphaBuf[d + 3] = (unsigned char)(cov * 255 / n);  // 覆盖度→AA半透明边缘
        }
    }

    Gdiplus::GdiplusStartupInput gsi;
    ULONG_PTR gdiToken = 0;
    savePngBgra(whiteBuf, Hw, Hh, prefix + ".png", gsi, gdiToken, true);
    savePngBgra(alphaBuf, Hw, Hh, prefix + "_t.png", gsi, gdiToken, false);
    Gdiplus::GdiplusShutdown(gdiToken);
    g_viewer.showMessage("期刊高清已导出: " + prefix + ".png / " + prefix + "_t.png");
}

static void drawOverlay(HDC hdc) {
    glMatrixMode(GL_PROJECTION); glPushMatrix();
    glMatrixMode(GL_MODELVIEW); glPushMatrix();
    glPushAttrib(GL_TRANSFORM_BIT | GL_CURRENT_BIT | GL_ENABLE_BIT);
    glLoadIdentity();
    glMatrixMode(GL_PROJECTION); glLoadIdentity();
    gluOrtho2D(0, (double)g_viewer.winW, 0, (double)g_viewer.winH);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_LIGHTING);

    const Molecule& mol = g_viewer.mol;
    wchar_t line[256];

    // 顶部信息栏
    swprintf(line, 256, L"原子: %d    键: %d    模式: %s",
             (int)mol.atoms.size(), (int)mol.bonds.size(),
             g_viewer.renderMode == MODE_BALLSTICK ? L"球棍模型" :
             g_viewer.renderMode == MODE_SPACEFILL ? L"比例模型" : L"线框模型");
    glColor3f(0.10f, 0.10f, 0.12f);
    drawTextW(hdc, 12.0f, (float)g_viewer.winH - 24.0f, line);

    if (!mol.title.empty()) {
        wchar_t wtitle[128];
        int n = MultiByteToWideChar(CP_UTF8, 0, mol.title.c_str(), -1, wtitle, 128);
        if (n > 0 && mol.title.size() < 120)
            drawTextW(hdc, 12.0f, (float)g_viewer.winH - 44.0f, wtitle);
    }

    // 测量模式状态行
    if (g_viewer.measureMode) {
        swprintf(line, 256, L"测量模式：已选 %d/4 原子  （空格=提交  Backspace=撤销  点空白处=重选）",
                 (int)g_viewer.selAtoms.size());
        glColor3f(0.80f, 0.30f, 0.05f);
        drawTextW(hdc, 12.0f, (float)g_viewer.winH - 64.0f, line);
    }

    // 悬停原子信息
    if (g_viewer.hoverAtom >= 0 && g_viewer.hoverAtom < (int)mol.atoms.size()
        && atomVisible(g_viewer.hoverAtom)) {
        const Atom& a = mol.atoms[g_viewer.hoverAtom];
        const ElementInfo& e = elemInfo(a.symbol);
        swprintf(line, 256, L"原子 %d: %S (%s)  (%.3f, %.3f, %.3f) Å",
                 g_viewer.hoverAtom + 1, a.symbol.c_str(), e.cnName,
                 a.pos.x, a.pos.y, a.pos.z);
        glColor3f(0.13f, 0.32f, 0.75f);
        drawTextW(hdc, 12.0f, (float)g_viewer.winH - 84.0f, line);
    }

    // 等值面层状态（FMO/电子云）
    if (!g_viewer.layers.empty()) {
        int fmoOn = 0, fmoAll = 0, denOn = 0, denAll = 0;
        for (auto& l : g_viewer.layers) {
            if (l.isFmo) { fmoAll++; if (l.enabled) fmoOn++; }
            else { denAll++; if (l.enabled) denOn++; }
        }
        swprintf(line, 256, L"等值面: FMO %d/%d (B切换)   密度 %d/%d (D切换)",
                 fmoOn, fmoAll, denOn, denAll);
        glColor3f(0.10f, 0.45f, 0.40f);
        drawTextW(hdc, 12.0f, (float)g_viewer.winH - 104.0f, line);
    }

    // 元素图例（左下角，提示行上方）：色块+彩色文字
    std::map<std::string, int> counts;
    for (auto& a : mol.atoms) counts[a.symbol]++;
    float ly = 56.0f;
    int shown = 0;
    for (auto& kv : counts) {
        if (shown++ >= 12) break;
        const ElementInfo& e = elemInfo(kv.first);
        // 色块（真实元素颜色）
        glBegin(GL_QUADS);
        glColor3f(e.r, e.g, e.b);
        glVertex2f(12.0f, ly + 3.0f);
        glVertex2f(26.0f, ly + 3.0f);
        glVertex2f(26.0f, ly + 15.0f);
        glVertex2f(12.0f, ly + 15.0f);
        glEnd();
        swprintf(line, 256, L"%S (%s): %d", kv.first.c_str(), e.cnName, kv.second);
        glColor3f(e.r * 0.6f, e.g * 0.6f, e.b * 0.6f);
        drawTextW(hdc, 32.0f, ly, line);
        ly += 20.0f;
    }

    // 底部操作提示（两行）
    const wchar_t* hints1 = L"左键拖动=旋转  左键点击=原子信息  滚轮=缩放  右键拖动=平移";
    const wchar_t* hints2 = L"1/2/3=球棍/比例/线框  H=氢开关  L=标签  M=测量  B=等值面  D=密度  "
                            L"S=截图  R=复位  F=适配  O=打开  Esc=退出";
    glColor3f(0.35f, 0.35f, 0.40f);
    drawTextW(hdc, 12.0f, 32.0f, hints1);
    drawTextW(hdc, 12.0f, 12.0f, hints2);

    // 临时消息
    if (g_viewer.messageValid) {
        if (GetTickCount() < g_viewer.messageUntil) {
            wchar_t wmsg[256];
            int n = MultiByteToWideChar(CP_UTF8, 0, g_viewer.message.c_str(), -1, wmsg, 256);
            if (n > 0) {
                glColor3f(0.75f, 0.20f, 0.20f);
                drawTextW(hdc, (float)g_viewer.winW - 320.0f, 12.0f, wmsg);
            }
        } else {
            g_viewer.messageValid = false;
        }
    }

    // 测量数值标签（renderMeasurements阶段投影缓存的屏幕坐标）
    if (!g_pendingLabels.empty()) {
        glColor3f(0.72f, 0.25f, 0.02f);
        for (const ScreenLabel& lbl : g_pendingLabels)
            drawTextW(hdc, lbl.x, lbl.y, lbl.text);
    }

    glEnable(GL_DEPTH_TEST);
    glPopAttrib();
    glMatrixMode(GL_PROJECTION); glPopMatrix();
    glMatrixMode(GL_MODELVIEW); glPopMatrix();
}

// ==================== 轨迹球 ====================
static Quat screenToArcball(int x, int y) {
    // 将屏幕坐标映射到虚拟球面 [-1,1]
    float vx = (2.0f * x / g_viewer.winW) - 1.0f;
    float vy = 1.0f - (2.0f * y / g_viewer.winH);
    float d2 = vx * vx + vy * vy;
    if (d2 <= 1.0f) {
        float z = std::sqrt(1.0f - d2);
        return Quat(0, vx, vy, z);
    }
    // 球外：取xz平面归一化
    float L = std::sqrt(d2);
    return Quat(0, vx / L, vy / L, 0);
}

// ==================== 窗口过程 ====================
static void openFileDlg(HWND hwnd) {
    wchar_t file[MAX_PATH] = L"";
    OPENFILENAMEW ofn = { sizeof(ofn) };
    ofn.hwndOwner = hwnd;
    ofn.lpstrFilter = L"分子文件 (*.xyz;*.mol)\0*.xyz;*.mol\0所有文件 (*.*)\0*.*\0";
    ofn.lpstrFile = file;
    ofn.nMaxFile = MAX_PATH;
    ofn.Flags = OFN_FILEMUSTEXIST | OFN_HIDEREADONLY;
    if (GetOpenFileNameW(&ofn)) {
        char path[MAX_PATH];
        WideCharToMultiByte(CP_UTF8, 0, file, -1, path, MAX_PATH, nullptr, nullptr);
        if (loadMolecule(path, g_viewer.mol)) {
            g_viewer.filePath = path;
            g_viewer.loaded = true;
            applyBondFixes(g_viewer.mol);
            g_viewer.resetView();
            loadMeshLayers(path);
            g_viewer.fitView();
            wchar_t title[MAX_PATH + 64];
            swprintf(title, MAX_PATH + 64, L"分子可视化 - %s", file);
            SetWindowTextW(hwnd, title);
        } else {
            g_viewer.showMessage(std::string("无法解析文件: ") + path);
        }
    }
}

static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    switch (msg) {
    case WM_CREATE:
        DragAcceptFiles(hwnd, TRUE);
        return 0;
    case WM_SIZE:
        g_viewer.winW = LOWORD(lParam) ? LOWORD(lParam) : 1;
        g_viewer.winH = HIWORD(lParam) ? HIWORD(lParam) : 1;
        return 0;
    case WM_PAINT: {
        PAINTSTRUCT ps;
        BeginPaint(hwnd, &ps);
        EndPaint(hwnd, &ps);
        return 0;
    }
    case WM_ERASEBKGND:
        return 1; // 防闪烁
    case WM_DROPFILES: {
        HDROP hDrop = (HDROP)wParam;
        wchar_t file[MAX_PATH];
        if (DragQueryFileW(hDrop, 0, file, MAX_PATH) > 0) {
            char path[MAX_PATH];
            WideCharToMultiByte(CP_UTF8, 0, file, -1, path, MAX_PATH, nullptr, nullptr);
            if (loadMolecule(path, g_viewer.mol)) {
                g_viewer.filePath = path;
                g_viewer.loaded = true;
                applyBondFixes(g_viewer.mol);
                g_viewer.resetView();
                loadMeshLayers(path);
                g_viewer.fitView();
                wchar_t title[MAX_PATH + 64];
                swprintf(title, MAX_PATH + 64, L"分子可视化 - %s", file);
                SetWindowTextW(hwnd, title);
            } else {
                g_viewer.showMessage(std::string("无法解析文件: ") + path);
            }
        }
        DragFinish(hDrop);
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_LBUTTONDOWN:
        SetCapture(hwnd);
        g_viewer.dragging = true;
        g_viewer.lastX = GET_X_LPARAM(lParam);
        g_viewer.lastY = GET_Y_LPARAM(lParam);
        g_viewer.downX = g_viewer.lastX;
        g_viewer.downY = g_viewer.lastY;
        return 0;
    case WM_LBUTTONUP: {
        g_viewer.dragging = false;
        ReleaseCapture();
        int x = GET_X_LPARAM(lParam), y = GET_Y_LPARAM(lParam);
        // 与按下位置位移小于阈值视为单击（拾取），否则是拖动旋转
        if (std::abs(x - g_viewer.downX) < 4 && std::abs(y - g_viewer.downY) < 4) {
            int picked = -1;
            if (pickAtom(x, y, picked)) {
                if (g_viewer.measureMode) {
                    auto& s = g_viewer.selAtoms;
                    auto it = std::find(s.begin(), s.end(), picked);
                    if (it != s.end()) s.erase(it); // 再次点击同一原子=取消
                    else {
                        s.push_back(picked);
                        if (s.size() >= 4) commitMeasureSelection(); // 满二面角自动提交
                    }
                } else {
                    const Atom& a = g_viewer.mol.atoms[picked];
                    const ElementInfo& e = elemInfo(a.symbol);
                    char cn[32];
                    WideCharToMultiByte(CP_UTF8, 0, e.cnName, -1, cn, 32, nullptr, nullptr);
                    char msg[160];
                    sprintf_s(msg, 160, "原子 %d: %s(%s)  (%.3f, %.3f, %.3f) Å",
                              picked + 1, a.symbol.c_str(), cn,
                              a.pos.x, a.pos.y, a.pos.z);
                    g_viewer.showMessage(msg);
                }
            } else if (g_viewer.measureMode && !g_viewer.selAtoms.empty()) {
                g_viewer.selAtoms.clear(); // 测量模式下点空白处重新选择
            }
        }
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_RBUTTONDOWN:
    case WM_MBUTTONDOWN:
        SetCapture(hwnd);
        g_viewer.panning = true;
        g_viewer.lastX = GET_X_LPARAM(lParam);
        g_viewer.lastY = GET_Y_LPARAM(lParam);
        return 0;
    case WM_RBUTTONUP:
    case WM_MBUTTONUP:
        g_viewer.panning = false;
        ReleaseCapture();
        return 0;
    case WM_MOUSEMOVE: {
        int x = GET_X_LPARAM(lParam), y = GET_Y_LPARAM(lParam);
        if (!g_trackMouse) {
            TRACKMOUSEEVENT tme = { sizeof(tme) };
            tme.dwFlags = TME_LEAVE;
            tme.hwndTrack = hwnd;
            TrackMouseEvent(&tme);
            g_trackMouse = true;
        }
        if (g_viewer.dragging) {
            // 增量轨迹球旋转
            Quat p1 = screenToArcball(g_viewer.lastX, g_viewer.lastY);
            Quat p2 = screenToArcball(x, y);
            Quat q = p2 * p1.conjugate();
            q.w = -q.w; // 旋转增量
            g_viewer.rotation = q * g_viewer.rotation;
            g_viewer.rotation.normalize();
            g_viewer.lastX = x; g_viewer.lastY = y;
            InvalidateRect(hwnd, nullptr, FALSE);
        } else if (g_viewer.panning) {
            float scale = g_viewer.dist / (float)g_viewer.winH;
            g_viewer.pan.x -= (x - g_viewer.lastX) * scale;
            g_viewer.pan.y += (y - g_viewer.lastY) * scale;
            g_viewer.lastX = x; g_viewer.lastY = y;
            InvalidateRect(hwnd, nullptr, FALSE);
        } else {
            // 悬停拾取
            int picked = -1;
            pickAtom(x, y, picked);
            if (picked != g_viewer.hoverAtom) {
                g_viewer.hoverAtom = picked;
                InvalidateRect(hwnd, nullptr, FALSE);
            }
        }
        return 0;
    }
    case WM_MOUSELEAVE:
        g_trackMouse = false;
        if (g_viewer.hoverAtom != -1) {
            g_viewer.hoverAtom = -1;
            InvalidateRect(hwnd, nullptr, FALSE);
        }
        return 0;
    case WM_MOUSEWHEEL: {
        float z = (float)GET_WHEEL_DELTA_WPARAM(wParam) / WHEEL_DELTA;
        g_viewer.dist *= (z > 0) ? 0.90f : 1.11f;
        g_viewer.dist = std::max(1.0f, std::min(500.0f, g_viewer.dist));
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_KEYDOWN: {
        FILE* kf = nullptr; fopen_s(&kf, "molview_keys.txt", "a");
        if (kf) { fprintf(kf, "WM_KEYDOWN vk=%u hwnd=%p\n", (unsigned)wParam, (void*)hwnd); fclose(kf); }
        switch (wParam) {
        case VK_ESCAPE: PostQuitMessage(0); return 0;
        case 'H':
            g_viewer.showHydrogen = !g_viewer.showHydrogen;
            g_viewer.showMessage(g_viewer.showHydrogen ? "已显示氢原子" : "已隐藏氢原子");
            break;
        case 'L': g_viewer.showLabels = !g_viewer.showLabels; break;
        case 'B': {
            FILE* kf = nullptr; fopen_s(&kf, "molview_keys.txt", "a");
            if (kf) { fprintf(kf, "B received, layers=%zu\n", g_viewer.layers.size()); fclose(kf); }
            bool any = false, on = false;
            for (auto& layer : g_viewer.layers)
                if (layer.isFmo) { layer.enabled = !layer.enabled; on = layer.enabled; any = true; }
            if (any) g_viewer.showMessage(on ? "FMO等值面：显示" : "FMO等值面：隐藏");
            break;
        }
        case 'D': {
            bool any = false, on = false;
            for (auto& layer : g_viewer.layers)
                if (!layer.isFmo) { layer.enabled = !layer.enabled; on = layer.enabled; any = true; }
            if (any) g_viewer.showMessage(on ? "密度等值面：显示" : "密度等值面：隐藏");
            break;
        }
        case 'R': g_viewer.resetView(); g_viewer.showMessage("视图已复位"); break;
        case 'F': g_viewer.fitView(); g_viewer.showMessage("已适配窗口"); break;
        case 'O': openFileDlg(hwnd); break;
        case '1': g_viewer.renderMode = MODE_BALLSTICK; g_viewer.showMessage("球棍模型"); break;
        case '2': g_viewer.renderMode = MODE_SPACEFILL; g_viewer.showMessage("比例模型（范德华半径）"); break;
        case '3': g_viewer.renderMode = MODE_WIRE; g_viewer.showMessage("线框模型"); break;
        case 'M':
            g_viewer.measureMode = !g_viewer.measureMode;
            g_viewer.selAtoms.clear();
            g_viewer.showMessage(g_viewer.measureMode ?
                "测量模式开：依次点选2~4个原子（距离/键角/二面角），空格提交" :
                "测量模式关（已提交测量保留，C清除）");
            break;
        case 'C':
            g_viewer.clearMeasures();
            g_viewer.showMessage("已清除全部测量");
            break;
        case 'S':
            g_screenshotPending = true;
            break;
        case 'P':
            if (g_hiresPrefix.empty()) g_hiresPrefix = "hires";
            g_hiresPending = true;   // 当前视角高清期刊导出
            break;
        case VK_SPACE:
        case VK_RETURN:
            if (g_viewer.measureMode && g_viewer.selAtoms.size() >= 2)
                commitMeasureSelection();
            break;
        case VK_BACK:
            if (!g_viewer.selAtoms.empty()) {
                g_viewer.selAtoms.pop_back();
                g_viewer.showMessage("已撤销上一个选择原子");
            }
            break;
        }
        InvalidateRect(hwnd, nullptr, FALSE);
        return 0;
    }
    case WM_DESTROY:
        if (hwnd == g_mainHwnd) PostQuitMessage(0); // 探测窗口销毁不退出
        return 0;
    case WM_CLOSE: {
        FILE* kf = nullptr; fopen_s(&kf, "molview_keys.txt", "a");
        if (kf) { fprintf(kf, "WM_CLOSE received\n"); fclose(kf); }
        DestroyWindow(hwnd);
        return 0;
    }
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

// ==================== 主循环 ====================
// 仅启用所选 FMO 层（homo|lumo|none|all），密度层在自动连拍时默认关闭以免干扰
static void setFmoSelection(const std::string& sel) {
    for (auto& l : g_viewer.layers) {
        bool isH = l.label.find("HOMO") != std::string::npos;
        bool isL = l.label.find("LUMO") != std::string::npos;
        if (sel == "homo")       l.enabled = l.isFmo && isH;
        else if (sel == "lumo")  l.enabled = l.isFmo && isL;
        else if (sel == "none")  l.enabled = false;
        else if (sel == "all")   l.enabled = l.isFmo;   // 默认：FMO 全开，密度关
        else                     l.enabled = false;
    }
}

// 聚焦到反应位点原子：将相机平移使该原子居中，并按局域配位与等值面范围取景
static void focusReactiveSite(int atomIdx) {
    Molecule& mol = g_viewer.mol;
    if (atomIdx < 0 || atomIdx >= (int)mol.atoms.size()) return;
    const Vec3 F = mol.atoms[atomIdx].pos;
    const Vec3 v = g_viewer.rotation.rotateVec(F - mol.center);
    g_viewer.pan.x = -v.x;
    g_viewer.pan.y = -v.y;
    float r = 3.2f;                       // 局域取景半径（Å）
    for (const auto& a : mol.atoms) {
        float d = (a.pos - F).len();
        if (d < 6.0f && d > r) r = d;     // 仅统计反应位近邻配位
    }
    float mR = 0.0f;                      // 反应位附近的 FMO 等值面极径
    for (const auto& L : g_viewer.layers) {
        const TriMesh& m = L.mesh;
        for (size_t i = 0; i < m.ntri; i++) {
            for (int k = 0; k < 3; k++) {
                float dx = m.data[i * 12 + k * 3] - F.x;
                float dy = m.data[i * 12 + k * 3 + 1] - F.y;
                float dz = m.data[i * 12 + k * 3 + 2] - F.z;
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > mR) mR = d2;
            }
        }
    }
    r = std::max(r, std::sqrt(mR));
    g_viewer.dist = std::max(9.0f, std::min(500.0f, r * 2.6f + 2.0f));
}

// 拆解命令行：{几何文件} --fmo --layers S --focus N --out P --frames N --size WxH
static void parseCmdLine(PWSTR cmdLine, std::wstring& geom) {
    std::vector<std::wstring> tok;
    {
        std::wstring cur; bool inQ = false;
        while (*cmdLine) {
            wchar_t c = *cmdLine++;
            if (c == L'"') { inQ = !inQ; continue; }
            if ((c == L' ' || c == L'\t') && !inQ) {
                if (!cur.empty()) { tok.push_back(cur); cur.clear(); }
            } else cur += c;
        }
        if (!cur.empty()) tok.push_back(cur);
    }
    auto need = [&](size_t& i, std::wstring& out) -> bool {
        if (i + 1 >= tok.size()) return false;
        out = tok[++i]; return true;
    };
    for (size_t i = 0; i < tok.size(); i++) {
        std::wstring t = tok[i];
        if (t == L"--fmo") { g_fmoAuto = true; continue; }
        if (t == L"--layers") { std::wstring s; if (need(i, s)) g_layersSel = std::string(s.begin(), s.end()); continue; }
        if (t == L"--focus") { std::wstring s; if (need(i, s)) g_focusAtom = _wtoi(s.c_str()); continue; }
        if (t == L"--out") { std::wstring s; if (need(i, s)) g_outPrefix = std::string(s.begin(), s.end()); continue; }
        if (t == L"--frames") { std::wstring s; if (need(i, s)) g_frames = _wtoi(s.c_str()); continue; }
        if (t == L"--size") {
            std::wstring s; if (need(i, s)) {
                int wc = 0, hc = 0;
                if (swscanf(s.c_str(), L"%dx%d", &wc, &hc) == 2 && wc > 0 && hc > 0) { g_winW = wc; g_winH = hc; }
            }
            continue;
        }
        if (t == L"--hi") { g_hires = true; std::wstring s; if (need(i, s)) g_hiresPrefix = std::string(s.begin(), s.end()); continue; }
        if (t == L"--px") {
            std::wstring s; if (need(i, s)) {
                int w = 0, h = 0;
                if (swscanf(s.c_str(), L"%dx%d", &w, &h) == 2 && w >= 512 && h >= 512) { g_hiresW = w; g_hiresH = h; }
            }
            continue;
        }
        if (t == L"--bondfix") {
            std::wstring s; if (need(i, s)) g_bondFixFile = std::string(s.begin(), s.end());
            continue;
        }
        if (t == L"--camera") {
            std::wstring s; if (need(i, s)) g_cameraFile = std::string(s.begin(), s.end());
            continue;
        }
        if (t == L"--help" || t == L"-h") {
            MessageBoxW(nullptr, L"molview.exe <几何文件> [--fmo] [--layers homo|lumo|none|all] [--focus <原子索引>] [--out <前缀>] [--frames <N>] [--size <WxH>] [--hi <前缀>] [--px <WxH>] [--bondfix <文件>] [--camera <文件>]",
                        L"用法", MB_ICONINFORMATION);
            ExitProcess(0);
        }
        if (geom.empty() && t[0] != L'-') geom = t;
    }
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR cmdLine, int) {
    // 命令行文件参数 + 自动化开关
    wchar_t fileArg[MAX_PATH] = L"";
    std::wstring geomArg;
    parseCmdLine(cmdLine, geomArg);
    if (!geomArg.empty()) {
        size_t n = geomArg.size() > MAX_PATH - 1 ? MAX_PATH - 1 : geomArg.size();
        wcsncpy_s(fileArg, MAX_PATH, geomArg.c_str(), _TRUNCATE);
    }

    WNDCLASSEXW wc = { sizeof(wc) };
    wc.style = CS_OWNDC;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.lpszClassName = L"ZhisuanMolView";
    RegisterClassExW(&wc);

    // ---- MSAA探测：临时窗口+临时上下文获取 wglChoosePixelFormatARB ----
    PFN_WGLCHOOSEPIXELFORMATARB wglChoosePixelFormatARB = nullptr;
    {
        HWND tmp = CreateWindowExW(0, wc.lpszClassName, L"probe", WS_POPUP,
                                   0, 0, 1, 1, nullptr, nullptr, hInst, nullptr);
        if (tmp) {
            HDC tdc = GetDC(tmp);
            PIXELFORMATDESCRIPTOR tpfd = { sizeof(tpfd) };
            tpfd.nVersion = 1;
            tpfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
            tpfd.iPixelType = PFD_TYPE_RGBA;
            tpfd.cColorBits = 24;
            tpfd.cDepthBits = 24;
            tpfd.iLayerType = PFD_MAIN_PLANE;
            int tpf = ChoosePixelFormat(tdc, &tpfd);
            if (tpf > 0 && SetPixelFormat(tdc, tpf, &tpfd)) {
                HGLRC trc = wglCreateContext(tdc);
                if (trc) {
                    wglMakeCurrent(tdc, trc);
                    wglChoosePixelFormatARB = (PFN_WGLCHOOSEPIXELFORMATARB)
                        wglGetProcAddress("wglChoosePixelFormatARB");
                    wglMakeCurrent(nullptr, nullptr);
                    wglDeleteContext(trc);
                }
            }
            ReleaseDC(tmp, tdc);
            DestroyWindow(tmp); // WM_DESTROY经g_mainHwnd守卫过滤，不触发退出
        }
    }

    HWND hwnd = CreateWindowExW(0, wc.lpszClassName, L"分子可视化",
                                WS_OVERLAPPEDWINDOW,
                                CW_USEDEFAULT, CW_USEDEFAULT, 1100, 760,
                                nullptr, nullptr, hInst, nullptr);
    if (!hwnd) return 1;
    g_mainHwnd = hwnd;
    ImmAssociateContext(hwnd, nullptr); // 禁用输入法，保证 B/D/H/L 等快捷键直达

    HDC hdc = GetDC(hwnd);
    // ---- 选择像素格式：优先MSAA(8/4/2x采样)，失败回退基础格式 ----
    int pf = 0;
    if (wglChoosePixelFormatARB) {
        for (int samples = 8; samples >= 2 && pf == 0; samples /= 2) {
            int attrs[] = {
                WGL_DRAW_TO_WINDOW_ARB, GL_TRUE,
                WGL_SUPPORT_OPENGL_ARB, GL_TRUE,
                WGL_DOUBLE_BUFFER_ARB, GL_TRUE,
                WGL_PIXEL_TYPE_ARB, WGL_TYPE_RGBA_ARB,
                WGL_COLOR_BITS_ARB, 24,
                WGL_DEPTH_BITS_ARB, 24,
                WGL_SAMPLE_BUFFERS_ARB, 1,
                WGL_SAMPLES_ARB, samples,
                0
            };
            UINT nfmt = 0;
            if (wglChoosePixelFormatARB(hdc, attrs, nullptr, 1, &pf, &nfmt) && nfmt > 0)
                break;
            pf = 0;
        }
    }
    PIXELFORMATDESCRIPTOR pfd = { sizeof(pfd) };
    if (pf > 0) {
        DescribePixelFormat(hdc, pf, sizeof(pfd), &pfd); // SetPixelFormat需与索引匹配的描述
    } else {
        pfd.nVersion = 1;
        pfd.dwFlags = PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
        pfd.iPixelType = PFD_TYPE_RGBA;
        pfd.cColorBits = 24;
        pfd.cDepthBits = 24;
        pfd.iLayerType = PFD_MAIN_PLANE;
        pf = ChoosePixelFormat(hdc, &pfd);
    }
    SetPixelFormat(hdc, pf, &pfd);
    HGLRC hglrc = wglCreateContext(hdc);
    wglMakeCurrent(hdc, hglrc);

    // 报告实际启用的MSAA采样数（旧SDK的PFD无采样字段，走扩展查询）
    {
        typedef BOOL (WINAPI *PFN_WGLGETPFATTRIBIVARB)(HDC, int, int, UINT, const int*, int*);
        PFN_WGLGETPFATTRIBIVARB wglGetPixelFormatAttribivARB =
            (PFN_WGLGETPFATTRIBIVARB)wglGetProcAddress("wglGetPixelFormatAttribivARB");
        if (wglGetPixelFormatAttribivARB) {
            int attrib = WGL_SAMPLES_ARB;
            int samples = 0;
            if (wglGetPixelFormatAttribivARB(hdc, GetPixelFormat(hdc), 0, 1, &attrib, &samples)
                && samples > 1) {
                char msg[64];
                sprintf_s(msg, 64, "MSAA已启用: %dx采样", samples);
                g_viewer.showMessage(msg);
            }
        }
    }

    buildFonts(hdc);
    g_quad = gluNewQuadric();
    gluQuadricNormals(g_quad, GLU_SMOOTH);

    // 命令行加载文件
    if (*fileArg) {
        char path[MAX_PATH];
        WideCharToMultiByte(CP_UTF8, 0, fileArg, -1, path, MAX_PATH, nullptr, nullptr);
        if (loadMolecule(path, g_viewer.mol)) {
            g_viewer.filePath = path;
            g_viewer.loaded = true;
            applyBondFixes(g_viewer.mol);
            g_viewer.resetView();
            loadMeshLayers(path);
            setFmoSelection(g_layersSel);
            if (g_focusAtom >= 0) focusReactiveSite(g_focusAtom);
            if (!g_cameraFile.empty()) applyCameraFile();
            wchar_t title[MAX_PATH + 64];
            swprintf(title, MAX_PATH + 64, L"分子可视化 - %s", fileArg);
            SetWindowTextW(hwnd, title);
        } else {
            g_viewer.showMessage(std::string("无法解析文件: ") + path);
        }
    } else {
        openFileDlg(hwnd);
        if (!g_viewer.loaded) {
            // 未选择文件则显示示例分子（水分子）
            g_viewer.mol.atoms = {{"O", {0.0f, 0.1173f, 0.0f}},
                                  {"H", {0.7572f, -0.4692f, 0.0f}},
                                  {"H", {-0.7572f, -0.4692f, 0.0f}}};
            g_viewer.mol.title = "H2O (示例分子)";
            detectBonds(g_viewer.mol);
            g_viewer.mol.computeExtent();
            g_viewer.loaded = true;
            g_viewer.resetView();
        }
    }

    // 背景渐变色（浅色主题，适合科研场景）
    glClearColor(0.965f, 0.965f, 0.975f, 1.0f);
    glEnable(GL_DEPTH_TEST);
    glEnable(GL_LIGHTING);
    glEnable(GL_LIGHT0);
    glEnable(GL_COLOR_MATERIAL);
    glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
    glEnable(GL_NORMALIZE);
    // 高光，增强球体和圆柱的立体感
    {
        GLfloat lightSpec[] = { 0.45f, 0.45f, 0.45f, 1.0f };
        GLfloat matSpec[] = { 0.35f, 0.35f, 0.35f, 1.0f };
        glLightfv(GL_LIGHT0, GL_SPECULAR, lightSpec);
        glMaterialfv(GL_FRONT_AND_BACK, GL_SPECULAR, matSpec);
        glMaterialf(GL_FRONT_AND_BACK, GL_SHININESS, 40.0f);
    }

    ShowWindow(hwnd, SW_SHOW);
    SetForegroundWindow(hwnd);
    SetFocus(hwnd);
    if (g_winW > 0 && g_winH > 0) {
        RECT rt = { 0, 0, g_winW, g_winH };
        AdjustWindowRectEx(&rt, GetWindowLongW(hwnd, GWL_STYLE), FALSE, GetWindowLongW(hwnd, GWL_EXSTYLE));
        SetWindowPos(hwnd, nullptr, 0, 0, rt.right - rt.left, rt.bottom - rt.top,
                     SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE);
        g_viewer.winW = g_winW; g_viewer.winH = g_winH;
    }

    MSG msg;
    bool done = false;
    // 自动连拍状态机：HOMO → 存图 → LUMO → 存图 → 退出
    int capPhase = -1, capFrame = 0;
    bool hiresHandled = false;   // --hi / 按键P：高清导出仅执行一次
    while (!done) {
        while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
            if (msg.message == WM_QUIT) done = true;
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        if (done) break;

        // ---- 渲染 ----
        wglMakeCurrent(hdc, hglrc);
        glViewport(0, 0, g_viewer.winW, g_viewer.winH);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        drawBackgroundGradient();

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(40.0, (double)g_viewer.winW / (double)g_viewer.winH, 0.1, 2000.0);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // 光源位置（跟随相机）
        GLfloat lightPos[] = { 0.4f, 0.5f, 1.0f, 0.0f };
        GLfloat lightAmb[] = { 0.38f, 0.38f, 0.38f, 1.0f };
        GLfloat lightDif[] = { 0.75f, 0.75f, 0.75f, 1.0f };
        glLightfv(GL_LIGHT0, GL_POSITION, lightPos);
        glLightfv(GL_LIGHT0, GL_AMBIENT, lightAmb);
        glLightfv(GL_LIGHT0, GL_DIFFUSE, lightDif);

        gluLookAt(0, 0, g_viewer.dist, 0, 0, 0, 0, 1, 0);
        glTranslatef(g_viewer.pan.x, g_viewer.pan.y, 0);

        float rm[16];
        g_viewer.rotation.toMatrix(rm);
        glMultMatrixf(rm);
        glTranslatef(-g_viewer.mol.center.x, -g_viewer.mol.center.y, -g_viewer.mol.center.z);

        if (g_viewer.loaded) {
            renderMolecule();
            renderMeshes(g_viewer.layers); // FMO/电子云等值面（半透明，画在分子之上）
            if (g_viewer.showLabels) renderLabels();
            renderMeasurements(); // 悬停/选中标记、测量虚线与数值标签
        }

        drawOverlay(hdc);

        if (g_screenshotPending) {
            g_screenshotPending = false;
            saveScreenshot(); // 交换前读取后台缓冲，捕获当前完整画面
        }

        if ((g_hires || g_hiresPending) && !hiresHandled) {
            hiresHandled = true;
            g_hiresPending = false;
            bool autoExit = g_hires;
            renderHiRes();
            if (autoExit) { PostQuitMessage(0); break; }
        }

        // ---- 自动连拍：HOMO → LUMO 各渲染 g_frames 帧后存图并退出 ----
        if (g_fmoAuto) {
            if (capPhase < 0) {
                capPhase = 0; capFrame = 0;
                setFmoSelection("homo");
            } else if (++capFrame >= g_frames) {
                if (capPhase == 0) {
                    saveScreenshot(g_outPrefix + "_homo");
                    capPhase = 1; capFrame = 0;
                    setFmoSelection("lumo");
                } else if (capPhase == 1) {
                    saveScreenshot(g_outPrefix + "_lumo");
                    capPhase = 2;
                }
            }
            if (capPhase == 2) { PostQuitMessage(0); }
        }
        SwapBuffers(hdc);
        Sleep(1); // 降低空闲CPU占用
    }

    gluDeleteQuadric(g_quad);
    wglMakeCurrent(nullptr, nullptr);
    wglDeleteContext(hglrc);
    ReleaseDC(hwnd, hdc);
    return (int)msg.wParam;
}
