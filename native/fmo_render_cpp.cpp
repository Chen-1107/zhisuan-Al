// fmo_render_cpp.cpp -- 纯 C++ 分子轨道渲染器（MSVC，无第三方库）
// 复刻 e2e_render3d.py + render_fmo_cloud.py 全管线：
//   cube 体数据(单一完整网格) -> Marching Tetra 提取 +ISO/−ISO 等值面
//   -> 球棍骨架(射线-球体 + z-buffer + Phong/Fresnel 边缘光)
//   -> 等值面前表面 z-buffer + 一次 alpha 半透明混合 -> 超采样降采样
// 输入: 轨道cube(.cub 一个即可,内外同网格取 ±) | 骨架xyz | Al,X(Se/Te) 索引 | tag | 输出bmp
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <string>
#include <vector>
#include <map>
#include <array>
#include <algorithm>

const double BOHR = 0.52917721092;

struct V3 { double x, y, z; };
static inline V3 vd(double x, double y, double z){ return {x,y,z}; }
static inline V3 operator+(V3 a, V3 b){ return {a.x+b.x,a.y+b.y,a.z+b.z}; }
static inline V3 operator-(V3 a, V3 b){ return {a.x-b.x,a.y-b.y,a.z-b.z}; }
static inline V3 operator*(V3 a, double s){ return {a.x*s,a.y*s,a.z*s}; }
static inline V3 operator*(double s, V3 a){ return a*s; }
static inline V3 operator/(V3 a, double s){ return {a.x/s,a.y/s,a.z/s}; }
static inline double dot(V3 a, V3 b){ return a.x*b.x+a.y*b.y+a.z*b.z; }
static inline V3 cross(V3 a, V3 b){ return {a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x}; }
static inline double norm(V3 a){ return std::sqrt(dot(a,a)); }
static inline V3 normalize(V3 a){ double n=norm(a); return n>1e-12? a/n : vd(0,0,1); }

// ---------------- 元素表（与 Python 一致） ----------------
static double DISP_R(const char* s){
    static const std::map<std::string,double> m={
      {"H",0.17},{"C",0.40},{"N",0.38},{"O",0.37},{"Al",0.56},{"Cl",0.45},
      {"Cu",0.47},{"Se",0.49},{"Te",0.55},{"F",0.30},{"P",0.47},{"S",0.46},
      {"B",0.42},{"Si",0.48},{"Br",0.47},{"I",0.52},{"Fe",0.47},{"Zn",0.45},
      {"Ni",0.44},{"Co",0.44},{"Cr",0.45},{"Mn",0.45},{"Ti",0.47},{"Mo",0.48},
      {"W",0.48},{"Mg",0.50},{"Ca",0.56},{"Li",0.52},{"Na",0.60},{"K",0.68},
      {"Ag",0.52},{"Au",0.52},{"Pt",0.52},{"Pd",0.50},{"Sn",0.55},{"Pb",0.58},
      {"Ga",0.50},{"Ge",0.50}};
    auto it=m.find(s); return it!=m.end()? it->second : 0.35;
}
static V3 ELEM_RGB(const char* s){
    static const std::map<std::string,V3> m={
      {"H",{0.94,0.94,0.94}},{"C",{0.22,0.22,0.22}},{"N",{0.16,0.30,0.96}},
      {"O",{1.00,0.17,0.17}},{"Al",{0.78,0.66,0.66}},{"Cl",{0.10,0.92,0.10}},
      {"Cu",{0.80,0.50,0.20}},{"Se",{1.00,0.62,0.00}},{"Te",{0.69,0.46,0.42}},
      {"F",{0.56,0.88,0.31}},{"P",{1.00,0.50,0.00}},{"S",{1.00,1.00,0.19}},
      {"B",{1.00,0.71,0.71}},{"Si",{0.94,0.78,0.63}},{"Br",{0.65,0.16,0.16}},
      {"I",{0.58,0.00,0.58}},{"Fe",{0.88,0.40,0.20}},{"Zn",{0.49,0.50,0.69}},
      {"Ni",{0.31,0.82,0.31}},{"Co",{0.94,0.56,0.63}},{"Cr",{0.54,0.60,0.78}},
      {"Mn",{0.61,0.48,0.78}},{"Ti",{0.75,0.76,0.78}},{"Mo",{0.33,0.69,0.69}},
      {"W",{0.13,0.62,0.69}},{"Mg",{0.54,1.00,0.00}},{"Ca",{0.24,1.00,0.00}},
      {"Li",{0.80,0.50,1.00}},{"Na",{0.67,0.36,0.95}},{"K",{0.56,0.25,0.83}},
      {"Ag",{0.75,0.75,0.75}},{"Au",{1.00,0.82,0.14}},{"Pt",{0.82,0.82,0.88}},
      {"Pd",{0.41,0.56,0.82}},{"Sn",{0.40,0.50,0.50}},{"Pb",{0.34,0.35,0.38}},
      {"Ga",{0.56,0.56,0.87}},{"Ge",{0.40,0.56,0.56}}};
    auto it=m.find(s); return it!=m.end()? it->second : vd(0.6,0.6,0.6);
}
static double BOND_COVR(const char* s){
    static const std::map<std::string,double> m={
      {"H",0.31},{"C",0.76},{"N",0.71},{"O",0.66},{"F",0.57},{"Cl",1.02},
      {"Br",1.20},{"I",1.39},{"S",1.05},{"P",1.07},{"B",0.84},{"Si",1.11},
      {"Al",1.21},{"Se",1.20},{"Te",1.38},{"Cu",1.32},{"Zn",1.22},{"Ni",1.24},
      {"Fe",1.32},{"Co",1.26},{"Cr",1.39},{"Mn",1.39},{"Ti",1.60},{"Mg",1.41},
      {"Na",1.66},{"K",2.03},{"Li",1.28},{"Ag",1.45},{"Au",1.36},{"Pt",1.36},
      {"Pd",1.39},{"Sn",1.39},{"Pb",1.46},{"Ga",1.22},{"Ge",1.20},{"Ca",1.76},
      {"Mo",1.54},{"W",1.62}};
    auto it=m.find(s); return it!=m.end()? it->second : 1.0;
}

// ---------------- cube 解析（单网格，axes 顺序 z,y,x） ----------------
struct Grid {
    int nx=0, ny=0, nz=0;
    V3 origin{0,0,0};
    double sx=0, sy=0, sz=0;
    std::vector<float> v; // (nz, ny, nx)
};
float gval(const Grid& g, int ix, int iy, int iz){
    return g.v[((size_t)iz*g.ny + iy)*g.nx + ix];
}
Grid parse_cube(const std::string& path){
    FILE* f=fopen(path.c_str(),"rb"); if(!f){fprintf(stderr,"cube open fail %s\n",path.c_str()); exit(1);}
    std::vector<std::string> L; char buf[4096];
    while(fgets(buf,sizeof buf,f)){ std::string b=buf; while(b.size()&&(b.back()=='\n'||b.back()=='\r'))b.pop_back(); L.push_back(b);}
    fclose(f);
    size_t idx=0;
    while(idx<L.size()){ const char* s=L[idx].c_str(); while(*s==' ')s++; if(*s&&(*s>='0'&&*s<='9'))break; idx++; }
    auto next_digit=[&](){ while(idx<L.size()){ const char* s=L[idx].c_str(); while(*s==' ')s++; if(*s&&(*s>='0'&&*s<='9'))break; idx++; } return idx<L.size(); };
    next_digit();
    int natoms; double ox,oy,oz;
    { char* e; const char* s=L[idx].c_str();
      natoms=(int)strtol(s,&e,10); ox=strtod(e,&e); oy=strtod(e,&e); oz=strtod(e,&e); }
    Grid g; g.origin=vd(ox*BOHR,oy*BOHR,oz*BOHR); idx++;
    int counts[3]={0,0,0}; double steps[3]={0,0,0};
    for(int a=0;a<3;a++){
        char* e; const char* s=L[idx].c_str();
        counts[a]=(int)strtol(s,&e,10);
        double p1=strtod(e,&e),p2=strtod(e,&e),p3=strtod(e,&e);
        double mx=0; for(double q: {p1*BOHR,-p1*BOHR,p2*BOHR,-p2*BOHR,p3*BOHR,-p3*BOHR}) if(std::fabs(q)>mx)mx=std::fabs(q);
        steps[a]=mx; idx++;
    }
    g.nx=counts[0]; g.ny=counts[1]; g.nz=counts[2];
    g.sx=steps[0]; g.sy=steps[1]; g.sz=steps[2];
    // 原子块（仅跳过）
    for(int a=0;a<natoms;a++) idx++;
    size_t total=(size_t)g.nx*g.ny*g.nz;
    g.v.resize(total);
    size_t k=0;
    while(idx<L.size() && k<total){
        const char* s=L[idx].c_str(); char* e;
        while(k<total){ double val=strtod(s,&e); if(e==s)break; g.v[k++]=(float)val; s=e; }
        idx++;
    }
    if(k<total){fprintf(stderr,"cube data short %zu/%zu\n",k,total);exit(1);}
    return g;
}

// ---------------- 3D 三线性加密 2x（各向同性） ----------------
Grid upscale2(const Grid& g){
    int nxn=g.nx*2-1, nyn=g.ny*2-1, nzn=g.nz*2-1;
    Grid r; r.nx=nxn; r.ny=nyn; r.nz=nzn;
    r.origin=g.origin; r.sx=g.sx/2; r.sy=g.sy/2; r.sz=g.sz/2;
    r.v.resize((size_t)nxn*nyn*nzn);
    int nx=g.nx, ny=g.ny, nz=g.nz;
    for(int iz=0; iz<nzn; iz++){
        double zf=0.5*iz; int z0=(int)zf; int z1=std::min(z0+1,nz-1); double fz=zf-z0;
        for(int iy=0; iy<nyn; iy++){
            double yf=0.5*iy; int y0=(int)yf; int y1=std::min(y0+1,ny-1); double fy=yf-y0;
            for(int ix=0; ix<nxn; ix++){
                double xf=0.5*ix; int x0=(int)xf; int x1=std::min(x0+1,nx-1); double fx=xf-x0;
                double f00=gval(g,x0,y0,z0)*(1-fx)+gval(g,x1,y0,z0)*fx;
                double f10=gval(g,x0,y1,z0)*(1-fx)+gval(g,x1,y1,z0)*fx;
                double f01=gval(g,x0,y0,z1)*(1-fx)+gval(g,x1,y0,z1)*fx;
                double f11=gval(g,x0,y1,z1)*(1-fx)+gval(g,x1,y1,z1)*fx;
                double a0=f00*(1-fy)+f10*fy;
                double a1=f01*(1-fy)+f11*fy;
                r.v[((size_t)iz*nyn+iy)*nxn+ix]=(float)(a0*(1-fz)+a1*fz);
            }
        }
    }
    return r;
}

// ---------------- Marching Tetrahedra（复刻 mo_mesh.py） ----------------
static const int TET_LIST[6][4]={{0,1,3,4},{1,3,4,6},{1,2,3,6},{1,4,5,6},{2,3,6,7},{3,4,6,7}};
static const int TET_EDGES[6][2]={{0,1},{1,2},{2,0},{0,3},{1,3},{2,3}};
static const int SINGLE_ORDER[4][3]={{2,0,3},{0,1,4},{1,2,5},{3,4,5}};

// 返回一个 case 的三角形（边索引三元组列表），复刻 _tri_for_case
std::vector<std::array<int,3>> tri_for_case(int c){
    std::vector<std::array<int,3>> out;
    if(c==0||c==0xF) return out;
    int n_in=0; for(int i=0;i<4;i++) if((c>>i)&1) n_in++;
    if(n_in==1){
        int v=-1; for(int i=0;i<4;i++) if((c>>i)&1) v=i;
        out.push_back({SINGLE_ORDER[v][0],SINGLE_ORDER[v][1],SINGLE_ORDER[v][2]});
    } else if(n_in==3){
        int outer=-1; for(int i=0;i<4;i++) if(!((c>>i)&1)) outer=i;
        out.push_back({SINGLE_ORDER[outer][0],SINGLE_ORDER[outer][1],SINGLE_ORDER[outer][2]});
    } else { // n_in==2
        int iv[2], ni=0; for(int i=0;i<4;i++) if((c>>i)&1) iv[ni++]=i;
        // 相交边
        std::vector<int> inter;
        for(int e=0;e<6;e++){ int a=TET_EDGES[e][0],b=TET_EDGES[e][1]; if(((c>>a)&1)!=((c>>b)&1)) inter.push_back(e);}
        int v0=iv[0],v1=iv[1];
        auto end_other=[&](int e,int vv){ int a=TET_EDGES[e][0],b=TET_EDGES[e][1]; return a==vv?b:a; };
        auto sel=[&](const std::vector<int>& vv,int want){ for(int e:vv) if(end_other(e,want)>-1)return e; return vv[0]; };
        // python 逻辑：从 v0 出发找匹配的环
        std::vector<int> ea,eb;
        for(int e:inter){ int a=TET_EDGES[e][0],b=TET_EDGES[e][1]; if(a==v0||b==v0)ea.push_back(e); if(a==v1||b==v1)eb.push_back(e);}
        std::vector<int> seq;
        auto try_end=[&](int e0){ // 返回是否成功，填充 seq
            std::vector<int> s; s.push_back(e0);
            int cur=end_other(e0,v0);
            int nxt=-1; for(int e:eb) if(cur==TET_EDGES[e][0]||cur==TET_EDGES[e][1]) nxt=e;
            if(nxt<0) return false;
            s.push_back(nxt);
            int ob=-1; for(int e:eb) if(e!=nxt) ob=e;
            if(ob<0) return false;
            s.push_back(ob);
            int last=end_other(ob,v1);
            int back=-1; for(int e:ea) if(last==TET_EDGES[e][0]||last==TET_EDGES[e][1]) back=e;
            if(back<0){ for(int e:ea) if(!(s.size()>0&&s[0]==e)){ back=e; break; } }
            if(back<0) return false;
            s.push_back(back);
            seq=s; return true;
        };
        bool ok=try_end(ea[0]); if(!ok){ seq.clear(); try_end(ea.back()); }
        out.push_back({seq[0],seq[1],seq[2]});
        out.push_back({seq[0],seq[2],seq[3]});
    }
    return out;
}

// 等值面提取，返回逐三角形顶点(n*3)与平面法线(n).
void marching_tetra(const Grid& g, double iso, std::vector<V3>& out_vert, std::vector<V3>& out_nrm){
    out_vert.clear(); out_nrm.clear();
    int nx=g.nx, ny=g.ny, nz=g.nz;
    for(int iz=0; iz<nz-1; iz++){
        double z0=g.origin.z+iz*g.sz, z1=z0+g.sz;
        for(int iy=0; iy<ny-1; iy++){
            double y0=g.origin.y+iy*g.sy, y1=y0+g.sy;
            for(int ix=0; ix<nx-1; ix++){
                double x0=g.origin.x+ix*g.sx, x1=x0+g.sx;
                float v[8]={gval(g,ix,iy,iz),gval(g,ix+1,iy,iz),gval(g,ix+1,iy+1,iz),gval(g,ix,iy+1,iz),
                            gval(g,ix,iy,iz+1),gval(g,ix+1,iy,iz+1),gval(g,ix+1,iy+1,iz+1),gval(g,ix,iy+1,iz+1)};
                int inner=0; for(int k2=0;k2<8;k2++) if(v[k2]>iso) inner|=(1<<k2);
                if(inner==0||inner==255) continue;
                V3 co[8]={vd(x0,y0,z0),vd(x1,y0,z0),vd(x1,y1,z0),vd(x0,y1,z0),
                          vd(x0,y0,z1),vd(x1,y0,z1),vd(x1,y1,z1),vd(x0,y1,z1)};
                for(int t=0;t<6;t++){
                    int a0=TET_LIST[t][0],a1=TET_LIST[t][1],a2=TET_LIST[t][2],a3=TET_LIST[t][3];
                    int t_inner=0;
                    if(inner&(1<<a0))t_inner|=1;
                    if(inner&(1<<a1))t_inner|=2;
                    if(inner&(1<<a2))t_inner|=4;
                    if(inner&(1<<a3))t_inner|=8;
                    auto tris=tri_for_case(t_inner);
                    if(tris.empty()) continue;
                    double tv[4]={v[a0],v[a1],v[a2],v[a3]}; V3 tp[4]={co[a0],co[a1],co[a2],co[a3]};
                    auto interp=[&](int e)->V3{ int u=TET_EDGES[e][0],w=TET_EDGES[e][1];
                        double va=tv[u],vb=tv[w]; double t;
                        if(std::fabs(vb-va)>1e-12){ t=(iso-va)/(vb-va); if(t!=t)t=0.5; else t=std::max(0.0,std::min(1.0,t));}
                        else t=0.5;
                        return tp[u]+t*(tp[w]-tp[u]); };
                    for(auto& tr: tris){
                        V3 p0=interp(tr[0]),p1=interp(tr[1]),p2=interp(tr[2]);
                        V3 n=cross(p1-p0,p2-p0); double ln=norm(n);
                        n = ln>1e-12? n/ln : vd(0,0,1);
                        out_vert.push_back(p0); out_vert.push_back(p1); out_vert.push_back(p2);
                        out_nrm.push_back(n); out_nrm.push_back(n); out_nrm.push_back(n);
                    }
                }
            }
        }
    }
}

// ---------------- 骨架场景 ----------------
struct Sphere { V3 c; double r; V3 rgb; double dim; };
std::vector<Sphere> build_skeleton(const std::vector<std::string>& syms, const std::vector<V3>& extra, int Wpx){
    return {};
}

// 检测键
std::vector<std::pair<int,int>> detect_bonds(const std::vector<std::string>& syms, const std::vector<V3>& xyz){
    std::vector<std::pair<int,int>> out;
    int n=(int)syms.size();
    for(int i=0;i<n;i++){ double ri=BOND_COVR(syms[i].c_str());
        for(int j=i+1;j<n;j++){ if(syms[i]=="H"&&syms[j]=="H")continue;
            double rj=BOND_COVR(syms[j].c_str()); double d=norm(xyz[i]-xyz[j]);
            if(0.4<d && d<ri+rj+0.45) out.push_back({i,j}); } }
    return out;
}

// 球链产生骨架球
void add_spheres_for_molecule(std::vector<Sphere>& sc, const std::vector<std::string>& syms,
                              const std::vector<V3>& view, double atom_scale=1.0,
                              double bond_r=0.085, double step=0.055){
    for(size_t i=0;i<syms.size();i++) sc.push_back({view[i], DISP_R(syms[i].c_str())*atom_scale, ELEM_RGB(syms[i].c_str()), 1.0});
    auto bonds=detect_bonds(syms,view);
    for(auto& b: bonds){
        V3 p=view[b.first], q=view[b.second]; double d=norm(q-p);
        if(d<1e-6) continue;
        V3 ca=ELEM_RGB(syms[b.first].c_str()), cb=ELEM_RGB(syms[b.second].c_str());
        int n=(int)std::ceil(d/step)+1; if(n<2)n=2;
        for(int k=0;k<n;k++){ double t=(double)k/(n-1); V3 c=p+(q-p)*t; V3 col=t<0.5?ca:cb;
            sc.push_back({c,bond_r,col,1.0}); }
    }
}

// 透视相机参数
struct CamInfo { double f, D, ss, W, H, center_x, center_y; double half; };
// 渲染：先球体，后三角形 front-surface z-buffer alpha
struct Img {
    int W,H,ss; int Wss,Hss; double half; double D, f;
    std::vector<double> zbuf; std::vector<float> img; // img RGB float (Wss*Hss*3)
    Img(int W_, int H_, int ss_, double fov_deg, double D_){
        W=W_; H=H_; ss=ss_; Wss=W*ss; Hss=H*ss;
        half=std::tan(fov_deg*3.14159265358979/360.0);
        this->D=D_; this->f=0.5*Hss/half;
        zbuf.assign((size_t)Wss*Hss, 1e30);
        img.assign((size_t)Wss*Hss*3, 1.0f);
    }
    double proj_x(double x,double z)const{ double denom=z+D; return Wss/2.0 + f*x/denom; }
    double proj_y(double y,double z)const{ double denom=z+D; return Hss/2.0 + f*y/denom; }
    double proj_r(double r,double z)const{ double denom=z+D; return f*r/denom*1.06; }
    void set_px(int i,int j,const V3& col){ size_t k=((size_t)j*Wss+i)*3; img[k]=col.x; img[k+1]=col.y; img[k+2]=col.z; }
};

// 光（相机 -z 指向，左上前）
const V3 LIGHT_L = normalize(vd(-0.42,0.55,-0.72));
const double SPEC_STR=0.80, SPEC_POW=38, AMBIENT=0.30, DIFFUSE=0.64, FOG_MAX=0.30;

void render_spheres(const std::vector<Sphere>& sc, Img& im){
    double zlo=1e30,zhi=-1e30;
    for(auto& s: sc){ if(s.c.z<zlo)zlo=s.c.z; if(s.c.z>zhi)zhi=s.c.z; }
    double zspan=std::max(zhi-zlo,1e-6);
    for(auto& s: sc){
        double denom=s.c.z+im.D; if(denom<=s.r+1e-9) continue;
        double cu=im.f*s.c.x/denom, cv=im.f*s.c.y/denom, rpx=im.proj_r(s.r,s.c.z);
        int i0=std::max((int)std::floor(cu-rpx-1)+im.Wss/2,0), i1=std::min((int)std::ceil(cu+rpx+1)+im.Wss/2, im.Wss);
        int j0=std::max((int)std::floor(cv-rpx-1)+im.Hss/2,0), j1=std::min((int)std::ceil(cv+rpx+1)+im.Hss/2, im.Hss);
        if(i0>=i1||j0>=j1) continue;
        double a_ang=im.D/im.f;
        for(int jj=j0;jj<j1;jj++) for(int ii=i0;ii<i1;ii++){
            double ux=((ii-im.Wss/2)+0.5)*a_ang, vy=((jj-im.Hss/2)+0.5)*a_ang;
            double ln=std::sqrt(ux*ux+vy*vy+im.D*im.D);
            double dx=ux/ln, dy=vy/ln, dz=im.D/ln;
            double ocx=-s.c.x, ocy=-s.c.y, ocz=-im.D-s.c.z;
            double b=ocx*dx+ocy*dy+ocz*dz;
            double q=ocx*ocx+ocy*ocy+ocz*ocz-s.r*s.r;
            double disc=b*b-q; if(disc<=0) continue;
            double sq=std::sqrt(disc); double t=-b-sq;
            size_t kp=((size_t)jj*im.Wss+ii);
            if(t<=0||t>=im.zbuf[kp]) continue;
            double hx=ocx+t*dx, hy=ocy+t*dy, hz=ocz+t*dz;
            double ir=1.0/s.r; double nx=hx*ir, ny=hy*ir, nz=hz*ir;
            double diff=nx*LIGHT_L.x+ny*LIGHT_L.y+nz*LIGHT_L.z; if(diff<0)diff=0;
            double hvx=LIGHT_L.x-dx,hvy=LIGHT_L.y-dy,hvz=LIGHT_L.z-dz;
            double hln=std::sqrt(hvx*hvx+hvy*hvy+hvz*hvz)+1e-12;
            double ndh=(nx*hvx+ny*hvy+nz*hvz)/hln; if(ndh<0)ndh=0;
            double spec=std::pow(ndh,SPEC_POW);
            double ndv=-(nx*dx+ny*dy+nz*dz); if(ndv<0)ndv=0;
            double edge=0.72+0.28*ndv;
            double fog=std::min(std::max((s.c.z-zlo)/zspan,0.0),1.0)*FOG_MAX;
            double lam=(AMBIENT+DIFFUSE*diff)*(1.0-fog);
            double r=s.rgb.x*s.dim*lam+SPEC_STR*spec; if(r<0)r=0; else if(r>1)r=1; r*=edge;
            double g=s.rgb.y*s.dim*lam+SPEC_STR*spec; if(g<0)g=0; else if(g>1)g=1; g*=edge;
            double b2=s.rgb.z*s.dim*lam+SPEC_STR*spec; if(b2<0)b2=0; else if(b2>1)b2=1; b2*=edge;
            im.img[kp*3]=r; im.img[kp*3+1]=g; im.img[kp*3+2]=b2;
            im.zbuf[kp]=t;
        }
    }
}

// ---- FMO 等值面双色半透明叠加 ----
struct FMOCfg { double iso, alpha, spec_pow, ambient, diffuse, spec; V3 light; double max_edge; };
void render_fmo_tris(const std::vector<V3>& vert, const std::vector<V3>& nrm, const V3& rgb,
                     const FMOCfg& cfg, Img& im, std::vector<double>& zbuf, std::vector<V3>& cbuf){
    int ntri=(int)vert.size()/3;
    for(int k=0;k<ntri;k++){
        V3 p0=vert[k*3], p1=vert[k*3+1], p2=vert[k*3+2];
        double e0=norm(p1-p0), e1=norm(p2-p1), e2=norm(p0-p2);
        if(e0>cfg.max_edge||e1>cfg.max_edge||e2>cfg.max_edge) continue;
        double z0=p0.z,z1=p1.z,z2=p2.z;
        double cxp0=im.proj_x(p0.x,p0.z), cyp0=im.proj_y(p0.y,p0.z);
        double cxp1=im.proj_x(p1.x,p1.z), cyp1=im.proj_y(p1.y,p1.z);
        double cxp2=im.proj_x(p2.x,p2.z), cyp2=im.proj_y(p2.y,p2.z);
        double xmin=std::max(std::floor(std::min({cxp0,cxp1,cxp2})),0.0);
        double xmax=std::min(std::ceil(std::max({cxp0,cxp1,cxp2})),im.Wss-1.0);
        double ymin=std::max(std::floor(std::min({cyp0,cyp1,cyp2})),0.0);
        double ymax=std::min(std::ceil(std::max({cyp0,cyp1,cyp2})),im.Hss-1.0);
        V3 n0=nrm[k*3],n1=nrm[k*3+1],n2=nrm[k*3+2];
        double ax0=cxp0, ay0=cyp0, ax1=cxp1, ay1=cyp1, ax2=cxp2, ay2=cyp2;
        double e0x=ax1-ax0,e0y=ay1-ay0,e1x=ax2-ax0,e1y=ay2-ay0;
        double den=e0x*e1y-e0y*e1x; if(std::fabs(den)<1e-12) continue;
        if(xmin>xmax||ymin>ymax) continue;
        for(int jj=(int)ymin;jj<=(int)ymax;jj++) for(int ii=(int)xmin;ii<=(int)xmax;ii++){
            double px=ii+0.5, py=jj+0.5;
            double b1=((px-ax0)*e1y-(py-ay0)*e1x)/den;
            double b2=((py-ay0)*e0x-(px-ax0)*e0y)/den;
            double b0=1.0-b1-b2;
            if(b0<0||b1<0||b2<0) continue;
            double depth=b0*z0+b1*z1+b2*z2;
            size_t kp=((size_t)jj*im.Wss+ii);
            if(depth>=zbuf[kp]) continue;
            double nxx=b0*n0.x+b1*n1.x+b2*n2.x;
            double nxy=b0*n0.y+b1*n1.y+b2*n2.y;
            double nxz=b0*n0.z+b1*n1.z+b2*n2.z;
            double nn=std::sqrt(nxx*nxx+nxy*nxy+nxz*nxz); if(nn<1e-9)nn=1; nxx/=nn;nxy/=nn;nxz/=nn;
            double ndl=nxx*cfg.light.x+nxy*cfg.light.y+nxz*cfg.light.z;
            double diff=ndl<0?-ndl:ndl;          // 双面
            V3 hv=cfg.light+vd(0,0,1); hv=normalize(hv);
            double ndh=nxx*hv.x+nxy*hv.y+nxz*hv.z; if(ndh<0)ndh=0;
            double spec=std::pow(ndh,cfg.spec_pow);
            double lam=cfg.ambient+cfg.diffuse*diff+cfg.spec*spec;
            V3 col=vd(rgb.x*lam, rgb.y*lam, rgb.z*lam);
            if(col.x>1)col.x=1; if(col.y>1)col.y=1; if(col.z>1)col.z=1;
            cbuf[kp]=col; zbuf[kp]=depth;
        }
    }
}

// 等值面投影到视图坐标（复刻 render_fmo_cloud：先减 mid3 再 Rm^T 再 R_VIEW^T）
// 这里外部已生成 tris_v,nrms_v（已转换），本函数仅做投影即可 —— 在上层处理。

// 图像降采样
std::vector<unsigned char> downsample(Img& im){
    std::vector<unsigned char> out((size_t)im.W*im.H*3);
    for(int j=0;j<im.H;j++) for(int i=0;i<im.W;i++){
        double r=0,g=0,b2=0;
        for(int dj=0;dj<im.ss;dj++) for(int di=0;di<im.ss;di++){
            size_t k=((size_t)(j*im.ss+dj)*im.Wss + i*im.ss+di)*3;
            r+=im.img[k]; g+=im.img[k+1]; b2+=im.img[k+2];
        }
        int n=im.ss*im.ss;
        out[((size_t)j*im.W+i)*3]=(unsigned char)(255.0*(r/n)+0.5);
        out[((size_t)j*im.W+i)*3+1]=(unsigned char)(255.0*(g/n)+0.5);
        out[((size_t)j*im.W+i)*3+2]=(unsigned char)(255.0*(b2/n)+0.5);
    }
    return out;
}

void write_bmp(const std::string& path, const std::vector<unsigned char>& rgb, int W, int H){
    FILE* f=fopen(path.c_str(),"wb");
    int row=(W*3+3)&~3; int pad=row-W*3;
    unsigned char hdr[54]; memset(hdr,0,54);
    hdr[0]='B';hdr[1]='M';
    unsigned int fs=54+(unsigned)row*H; memcpy(hdr+2,&fs,4);
    hdr[10]=54;
    hdr[14]=40; unsigned short planes=1; unsigned short bpp=24;
    memcpy(hdr+14+4,&W,4); int HH=-H; memcpy(hdr+14+8,&HH,4); // top-down
    memcpy(hdr+14+12,&planes,2); // biPlanes
    memcpy(hdr+14+14,&bpp,2);    // biBitCount
    unsigned int imgbytes=(unsigned)row*H; memcpy(hdr+14+20,&imgbytes,4);
    fwrite(hdr,1,54,f);
    std::vector<unsigned char> tmp(row);
    for(int j=0;j<H;j++){
        memcpy(tmp.data(), rgb.data()+(size_t)j*W*3, (size_t)W*3);
        if(pad) memset(tmp.data()+W*3,0,pad);
        fwrite(tmp.data(),1,row,f);
    }
    fclose(f);
}

// ==================== 主流程 ====================
struct Args { std::string cube, xyz, out; std::string tag; int i_al, i_x; };

V3 rot_apply(const V3& v, const V3 R[3]){ // 行向量 v @ R^T = R 视为矩阵作用于 v
    // Python: view=(xyz-mid3) @ Rm.T ;  Rm 行 = a,c,b。等价 v'=Rm*v
    return vd(R[0].x*v.x+R[0].y*v.y+R[0].z*v.z,
              R[1].x*v.x+R[1].y*v.y+R[1].z*v.z,
              R[2].x*v.x+R[2].y*v.y+R[2].z*v.z);
}

int main(int argc, char** argv){
    if(argc<7){ fprintf(stderr,"usage: fmo_render_cpp <cube> <xyz> <tag> <i_al> <i_x> <out.bmp>\n"); return 1; }
    std::string cube=argv[1], xyzpath=argv[2], tag=argv[3];
    int i_al=atoi(argv[4]), i_x=atoi(argv[5]); std::string out=argv[6];

    // 读骨架
    FILE* fx=fopen(xyzpath.c_str(),"r");
    std::vector<std::string> syms; std::vector<V3> xyz;
    char line[1024];
    while(fgets(line,sizeof line,fx)){
        if(isdigit((unsigned char)line[0])) continue;
        char s[32]; double a,b,c;
        if(sscanf(line,"%30s %lf %lf %lf",s,&a,&b,&c)==4){ syms.push_back(s); xyz.push_back(vd(a,b,c)); }
    }
    fclose(fx);
    int n=(int)syms.size();
    fprintf(stderr,"atoms=%d\n",n);

    // 读轨道网格（一个 cube，正负同网格）
    Grid g=parse_cube(cube);
    Grid gg=upscale2(g);
    fprintf(stderr,"grid %dx%dx%d -> %dx%dx%d\n",g.nx,g.ny,g.nz,gg.nx,gg.ny,gg.nz);

    const char* ei=getenv("ISO");  double ISO= ei? atof(ei):0.05;
    const char* ea=getenv("ALPHA"); double ALPHA= ea? atof(ea):0.60;
    const double CUBE_MAX_EDGE=2.6;
    // 正绿 负红
    V3 POS=vd(24/255.0,160/255.0,48/255.0);
    V3 NEG=vd(214/255.0,60/255.0,60/255.0);
    FMOCfg cfg; cfg.iso=ISO; cfg.alpha=ALPHA; cfg.spec_pow=10.0;
    cfg.ambient=0.50; cfg.diffuse=0.42; cfg.spec=0.10; cfg.max_edge=CUBE_MAX_EDGE;

    // build_view 复刻：Rm 行 = [a, c, b]
    V3 da=xyz[i_al]-xyz[i_x]; da=normalize(da);
    V3 mean=vd(0,0,0); for(auto& p: xyz) mean=mean+p; mean=mean*(1.0/n);
    V3 ref=mean-xyz[i_x]; ref=ref-da*dot(ref,da);
    if(norm(ref)<1e-6){ V3 z=vd(0,0,1); ref=z-da*dot(z,da); }
    V3 bv=normalize(ref); V3 cv=cross(da,bv);
    V3 Rm[3]={da,cv,bv};
    V3 mid3=vd((xyz[i_al].x+xyz[i_x].x)/2,(xyz[i_al].y+xyz[i_x].y)/2,(xyz[i_al].z+xyz[i_x].z)/2);

    // R_VIEW 硬编码（读自 _view_rot2.npy）
    V3 RV[3]={ vd(0.9396926207859084,0.3420201433256688,0.0),
               vd(-0.11697777844051098,0.3213938048432696,0.9396926207859083),
               vd(-0.32139380484326974,0.8830222215594891,-0.3420201433256687)};

    // 骨架视图坐标: (xyz-mid3) @ Rm.T @ R_VIEW.T
    std::vector<V3> view; view.reserve(n);
    for(int i=0;i<n;i++){ V3 t=rot_apply(xyz[i]-mid3,Rm); view.push_back(rot_apply(t,RV)); }

    // 整分子取景
    V3 cm=vd(0,0,0); for(auto& p: view) cm=cm+p; cm=cm*(1.0/n);
    double r_mol=0; for(auto& p: view){ double d=norm(p-cm); if(d>r_mol)r_mol=d; }
    double fov=22.0; double half=std::tan(fov*3.14159265358979/360.0);
    double camera_d=r_mol/half*1.35;

    // 构建骨架球
    Img im(1500,1500,3,fov,camera_d);
    std::vector<Sphere> sc; add_spheres_for_molecule(sc,syms,view,1.0,0.085,0.05);
    fprintf(stderr,"spheres=%zu camera_d=%.1f\n",sc.size(),camera_d);
    render_spheres(sc,im);

    // 等值面点转视图坐标
    auto to_view=[&](const V3& p){ V3 t=rot_apply(p-mid3,Rm); return rot_apply(t,RV); };
    std::vector<V3> pos_vert,pos_nrm,neg_vert,neg_nrm;
    marching_tetra(gg, ISO, pos_vert, pos_nrm);
    marching_tetra(gg, -ISO, neg_vert, neg_nrm);
    std::vector<V3> piv, pin, niv, nin;
    for(size_t i=0;i<pos_vert.size();i++) piv.push_back(to_view(pos_vert[i]));
    for(size_t i=0;i<pos_nrm.size();i++){ V3 t=rot_apply(pos_nrm[i],Rm); pin.push_back(rot_apply(t,RV)); }
    for(size_t i=0;i<neg_vert.size();i++) niv.push_back(to_view(neg_vert[i]));
    for(size_t i=0;i<neg_nrm.size();i++){ V3 t=rot_apply(neg_nrm[i],Rm); nin.push_back(rot_apply(t,RV)); }
    fprintf(stderr,"pos_tri=%zu neg_tri=%zu\n",piv.size()/3,niv.size()/3);

    // FMO 光方向（视图坐标）
    cfg.light=rot_apply(normalize(vd(0.4,0.3,0.86)), RV);

    // front-surface z-buffer 一次 alpha
    int Wss=im.Wss, Hss=im.Hss;
    std::vector<double> zbuf((size_t)Wss*Hss, 1e30);
    std::vector<V3> cbuf((size_t)Wss*Hss, vd(0,0,0));
    render_fmo_tris(piv,pin,POS,cfg,im,zbuf,cbuf);
    render_fmo_tris(niv,nin,NEG,cfg,im,zbuf,cbuf);
    size_t cnt=0; for(size_t k=0;k<(size_t)Wss*Hss;k++) if(zbuf[k]<1e29) cnt++;
    fprintf(stderr,"fmo_covered_px=%zu (of %zu)\n",cnt,(size_t)Wss*Hss);
    // 混合
    for(size_t k=0;k<(size_t)Wss*Hss;k++){
        if(zbuf[k]<1e29){
            im.img[k*3]  =(float)(im.img[k*3]*(1-ALPHA)+cbuf[k].x*ALPHA);
            im.img[k*3+1]=(float)(im.img[k*3+1]*(1-ALPHA)+cbuf[k].y*ALPHA);
            im.img[k*3+2]=(float)(im.img[k*3+2]*(1-ALPHA)+cbuf[k].z*ALPHA);
        }
    }
    auto rgb=downsample(im);
    write_bmp(out,rgb,im.W,im.H);
    fprintf(stderr,"wrote %s\n",out.c_str());
    return 0;
}