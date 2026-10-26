# 全能 MATLAB 技能手册（MATLAB_Skill）

> 本文档综合知识库中 MATLAB 官方中文文档（编程/数学/数据分析/导入导出）及化工工程应用教程，系统覆盖 MATLAB 从基础到高级的全部功能模块。

---

## 第一篇：MATLAB 基础入门

### 第一章 MATLAB 环境与基本操作 [pdf_450]

#### 1.1 MATLAB 桌面环境

MATLAB 桌面包含以下主要区域 [pdf_450]：

| 组件 | 功能 |
|------|------|
| **命令行窗口** | 在提示符 `>>` 后输入命令，执行计算 |
| **工作区（Workspace）** | 浏览已创建或从文件导入的变量 |
| **文件面板（Current Folder）** | 访问和管理文件 |
| **边栏** | 访问停靠在桌面上的其他工具 |

#### 1.2 基本变量与命令 [pdf_450]

**创建变量**：
```matlab
a = 1          % 创建变量 a = 1
b = 2          % 创建变量 b = 2
c = a + b      % 计算结果 c = 3
d = cos(a)     % 调用函数
```

**输出控制**：
- 语句以**分号**结尾 → 执行计算但**不显示输出**
- 语句无分号 → 在命令行窗口显示结果
- 未指定输出变量时，MATLAB 使用 `ans` 存储结果

**历史命令**：按 `↑` / `↓` 箭头键重新调用历史命令。

#### 1.3 帮助系统 [pdf_409]

```matlab
help functionname    % 在命令行查看函数帮助
doc functionname     % 打开文档浏览器
lookup keyword       % 搜索相关函数
ver                  % 查看已安装的工具箱版本
```

---

### 第二章 数组与矩阵操作 [pdf_450][pdf_451]

#### 2.1 创建矩阵 [pdf_450]

MATLAB 是 **Matrix Laboratory** 的缩写，所有数据均以矩阵/数组形式存储。

**手动创建**：
```matlab
A = [1 2 3; 4 5 6; 7 8 10]   % 3×3 矩阵，分号表示换行
u = [3; 1; 4]                 % 列向量 (3×1)
v = [2 0 -1]                  % 行向量 (1×3)
s = 7                         % 标量 (1×1)
```

**矩阵创建函数** [pdf_451]：

| 函数 | 说明 | 示例 |
|------|------|------|
| `zeros(m,n)` | 全零矩阵 | `A = zeros(3,2)` |
| `ones(m,n)` | 全 1 矩阵 | `B = ones(2,4)` |
| `eye(n)` | 单位矩阵 | `I = eye(3)` |
| `magic(n)` | 幻方矩阵 | `M = magic(4)` |
| `pascal(n)` | 帕斯卡矩阵 | `P = pascal(3)` |
| `rand(m,n)` | 均匀分布随机数 | `R = rand(3,4)` |
| `randi(k,m,n)` | 随机整数 | `C = randi(10,3,2)` |
| `diag(v)` | 对角矩阵 | `D = diag([12 62 93 -8])` |
| `linspace(a,b,n)` | 等间距向量 | `x = linspace(0,1,100)` |
| `logspace(a,b,n)` | 对数等间距向量 | `x = logspace(0,2,50)` |

#### 2.2 矩阵索引与切片 [pdf_450]

```matlab
A(i,j)         % 第 i 行第 j 列元素
A(i,:)         % 第 i 行所有列
A(:,j)         % 第 j 列所有行
A(1:3,2:4)     % 1~3 行、2~4 列子矩阵
A(end,:)       % 最后一行
A(1:2:end,:)   % 奇数行（步长为 2）
A(:)           % 所有元素展开为列向量
```

#### 2.3 数组运算与矩阵运算 [pdf_450][pdf_409]

MATLAB 有两种算术运算：**数组运算**（逐元素）和**矩阵运算**（线性代数）。

| 运算类型 | 数组运算（逐元素） | 矩阵运算（线性代数） |
|----------|:------------------:|:-------------------:|
| 加法 | `A + B` | `A + B`（相同） |
| 减法 | `A - B` | `A - B`（相同） |
| 乘法 | `A .* B` | `A * B` |
| 除法 | `A ./ B` | `A / B`（右除） |
| 左除 | — | `A \ B`（左除，解 AX = B） |
| 幂 | `A .^ n` | `A ^ n` |
| 转置 | `A .'`（非共轭） | `A'`（复共轭转置） |

**矩阵乘法核心公式** [pdf_451]：
$$C(i,j) = \sum_{k=1}^{n} A(i,k) B(k,j)$$

#### 2.4 矩阵拼接与重塑 [pdf_450]

```matlab
[A, B]          % 水平拼接（逗号分隔）
[A; B]          % 垂直拼接（分号分隔）
cat(3, A, B, C) % 沿第 3 维拼接 → 3D 数组
repmat(A, m, n) % 复制平铺矩阵
reshape(A, m, n)% 重塑矩阵形状
permute(A, [2 1 3]) % 维度重排
squeeze(A)      % 消除长度为 1 的维度
```

#### 2.5 隐式扩展 [pdf_450]

自 R2016b 起，MATLAB 支持**隐式扩展**（广播）：
```matlab
A = [1 1 1; 2 2 2; 3 3 3];  % 3×3
m = [2 4 6];                 % 1×3
A - m   % 自动扩展为 3×3 运算
```

---

## 第二篇：MATLAB 编程

### 第三章 脚本与函数 [pdf_450][pdf_409]

#### 3.1 脚本（Scripts）[pdf_450]

脚本是包含多行连续 MATLAB 命令的文件，扩展名为 `.m`。

```matlab
% mysphere.m - 创建并绘制球体
[x,y,z] = sphere;    % 创建单位球
r = 2;
surf(x*r, y*r, z*r)  % 调整尺寸并绘图
axis equal
A = 4*pi*r^2;        % 表面积
V = (4/3)*pi*r^3;    % 体积
```

运行脚本：在命令行输入 `mysphere` 或点击编辑器中的 **Run** 按钮。

#### 3.2 函数（Functions）[pdf_450]

**函数定义**：文件名与函数名必须相同。

```matlab
function r = rank(A, tol)
% RANK  Matrix rank.
%   RANK(A) provides an estimate of the number of linearly
%   independent rows or columns of a matrix A.
s = svd(A);
if nargin == 1
    tol = max(size(A)) * norm(A) * eps;
end
r = sum(s > tol);
end
```

**函数特性**：
- 处理**自己的工作区**中的变量，独立于全局工作区
- 可接受输入参量并返回输出参量
- `nargin` / `nargout`：检测输入/输出参数个数

#### 3.3 函数类型速查 [pdf_450]

| 类型 | 说明 | 示例 |
|------|------|------|
| 匿名函数 | 单行表达式 | `f = @(x) x.^2;` |
| 局部函数 | 文件内定义的辅助函数 | 主函数之后定义 |
| 嵌套函数 | 函数内部定义的函数 | 可访问父函数变量 |
| 私有函数 | `private` 文件夹中的函数 | 仅同文件夹函数可调用 |
| 重载函数 | 同名不同参数类型的函数 | 根据输入类型调度 |

#### 3.4 控制流 [pdf_409]

**条件语句**：
```matlab
if condition
    statements
elseif condition
    statements
else
    statements
end
```

**循环**：
```matlab
% for 循环
for i = 1:n
    statements
end

% while 循环
while condition
    statements
end
```

**switch-case**：
```matlab
switch expression
    case value1
        statements
    case value2
        statements
    otherwise
        statements
end
```

#### 3.5 运算符优先级 [pdf_409]

从高到低：括号 `()` → 转置 `'` → 幂 `^` → 一元负号 → 乘法除法 `* / \` → 加法减法 → 冒号 `:` → 关系运算符 → 逻辑与 `&` → 逻辑或 `|`

#### 3.6 逗号分隔列表 [pdf_409]

元胞数组和结构体可生成逗号分隔列表：
```matlab
C = {'LineWidth', 2, 'MarkerEdgeColor', 'k', 'MarkerFaceColor', 'g'};
plot(X, Y, '--rs', C{:})   % C{:} 展开为逗号分隔列表
```

#### 3.7 name=value 语法 [pdf_409]

自 R2021a 起支持：
```matlab
plot(x, y, LineWidth=2, Color='red')   % 新语法
plot(x, y, 'LineWidth', 2, 'Color', 'red')  % 传统语法
```

---

### 第四章 数据类型与数据结构 [pdf_409]

| 数据类型 | 说明 | 创建方式 |
|----------|------|----------|
| `double` | 双精度浮点（默认） | `a = 1.5` |
| `single` | 单精度浮点 | `a = single(1.5)` |
| `int8/16/32/64` | 有符号整数 | `a = int32(5)` |
| `uint8/16/32/64` | 无符号整数 | `a = uint8(255)` |
| `logical` | 逻辑值 | `a = true` |
| `char` | 字符向量 | `s = 'hello'` |
| `string` | 字符串数组 | `s = "hello"` |
| `cell` | 元胞数组 | `C = {1, 'text', [1 2 3]}` |
| `struct` | 结构体 | `S.name = 'test'; S.val = 10` |
| `table` | 表格 | `T = table(A, B, C)` |
| `datetime` | 日期时间 | `t = datetime('now')` |
| `categorical` | 分类数组 | `c = categorical({'A','B','C'})` |

---

## 第三篇：MATLAB 数学

### 第五章 线性代数 [pdf_451]

#### 5.1 线性方程组求解 [pdf_451]

**反斜杠运算符** `\`：MATLAB 中最核心的求解命令。

```matlab
A = pascal(3);
b = [1; 2; 3];
x = A \ b     % 解 Ax = b
```

**求解器自动选择策略** [pdf_451]：MATLAB 根据矩阵属性自动选择最优算法（LU、Cholesky、QR 等）。

**矩阵分解** [pdf_451]：

| 分解 | 函数 | 说明 |
|------|------|------|
| LU 分解 | `[L,U] = lu(A)` | `A = LU`，求解 `Ax = b` 最快 |
| QR 分解 | `[Q,R] = qr(A)` | `A = QR`，Q 正交 |
| Cholesky | `R = chol(A)` | 对称正定矩阵 `A = R^T R` |
| SVD | `[U,S,V] = svd(A)` | 奇异值分解 |
| 特征值 | `[V,D] = eig(A)` | `AV = VD` |
| Schur | `[U,T] = schur(A)` | `A = UTU^H` |

#### 5.2 矩阵范数与条件数 [pdf_451]

```matlab
norm(A)        % 矩阵 2-范数
norm(A, 'fro') % Frobenius 范数
cond(A)        % 条件数（越大越病态）
det(A)         % 行列式
rank(A)        % 秩
trace(A)       % 迹
```

#### 5.3 特征值与奇异值 [pdf_451]

```matlab
eig(A)          % 特征值向量
[V, D] = eig(A) % 特征向量矩阵 V，对角矩阵 D
svd(A)          % 奇异值向量
[U, S, V] = svd(A) % 完整 SVD
svds(A, k)      % 前 k 个最大奇异值（稀疏矩阵）
```

#### 5.4 稀疏矩阵 [pdf_451]

```matlab
S = sparse(i, j, v, m, n)   % 创建稀疏矩阵
full(S)                     % 转为满矩阵
nnz(S)                      % 非零元素个数
spy(S)                      % 可视化稀疏模式
ilu(A) / ichol(A)           % 不完全 LU/Cholesky 预条件子
```

**迭代求解器** [pdf_451]：

| 求解器 | 适用矩阵类型 | 说明 |
|--------|:------------:|------|
| `pcg` | 对称正定 | 预条件共轭梯度法 |
| `lsqr` | 任意 | 最小二乘，唯一适用于矩形 |
| `minres` | 对称但不一定正定 | 最小残差法 |
| `gmres` | 任意 | 广义最小残差法 |
| `bicgstab` | 任意 | 双共轭梯度稳定法 |

---

### 第六章 多项式与插值 [pdf_451]

#### 6.1 多项式表示与运算 [pdf_451]

MATLAB 用**行向量**表示多项式，系数按降幂排列：

```matlab
p = [1 -4 4];          % p(x) = x² - 4x + 4
q = [4 0 0 -3 2 33];   % q(x) = 4x⁵ - 3x² + 2x + 33

polyval(p, 2)          % 计算 p(2)
polyvalm(p, X)         % 矩阵方式计算 p(X)
roots(p)               % 求多项式的根
poly(A)                % 矩阵 A 的特征多项式
conv(p, q)             % 多项式乘法
deconv(p, q)           % 多项式除法
polyfit(x, y, n)       % n 次多项式拟合
polyder(p)             % 多项式微分
polyint(p)             % 多项式积分
```

#### 6.2 插值 [pdf_451]

```matlab
yi = interp1(x, y, xi, 'method')      % 一维插值
zi = interp2(x, y, z, xi, yi, 'method') % 二维插值
zi = interp3(x, y, z, v, xi, yi, zi)   % 三维插值
yi = spline(x, y, xi)                  % 三次样条插值
pp = pchip(x, y)                       % 保形分段三次插值
```

**插值方法**：`'linear'`（默认线性）、`'nearest'`（最近邻）、`'spline'`（三次样条）、`'pchip'`（保形）、`'cubic'`（立方）

---

### 第七章 数值积分与微分 [pdf_451]

```matlab
q = integral(fun, a, b)                % 一维数值积分
q = integral2(fun, xmin, xmax, ymin, ymax) % 二重积分
q = integral3(fun, xmin, xmax, ymin, ymax, zmin, zmax) % 三重积分
q = trapz(x, y)                        % 梯形法积分
d = diff(y)                            % 数值差分
d = gradient(y)                        % 数值梯度
```

---

### 第八章 优化 [pdf_451][pdf_10]

```matlab
x = fminsearch(fun, x0)                % 无约束优化（Nelder-Mead）
x = fmincon(fun, x0, A, b)             % 约束优化
x = fminbnd(fun, a, b)                 % 有界单变量优化
x = fzero(fun, x0)                     % 非线性方程求根
x = fsolve(fun, x0)                    % 非线性方程组求解
x = linprog(f, A, b)                   % 线性规划
x = lsqcurvefit(fun, x0, xdata, ydata) % 曲线拟合
```

**fmincon 算法选择**：`'active-set'`、`'interior-point'`、`'sqp'`、`'trust-region-reflective'` [pdf_10]

---

### 第九章 常微分方程（ODE）求解 [pdf_451]

#### 9.1 ODE 求解器概览 [pdf_451]

| 求解器 | 问题类型 | 精度 | 说明 |
|--------|:--------:|:----:|------|
| `ode45` | 非刚性 | 中 | 首选，4-5 阶 Runge-Kutta |
| `ode23` | 非刚性 | 低 | 低精度，比 ode45 快 |
| `ode113` | 非刚性 | 可变 | 变阶 Adams-Bashforth-Moulton |
| `ode15s` | 刚性 | 低~中 | 首选刚性求解器 |
| `ode23s` | 刚性 | 低 | 基于 Rosenbrock 公式 |
| `ode23t` | 中等刚性 | 低 | 梯形法 |
| `ode23tb` | 刚性 | 低 | TR-BDF2 |
| `ode15i` | 完全隐式 | 低~中 | 适用于 `f(t,y,y')=0` |

**ODE 求解标准用法**：
```matlab
% 定义 ODE 函数
function dydt = odefun(t, y)
    dydt = -2 * y;
end

% 求解
[t, y] = ode45(@odefun, [0 5], 1);  % [时间区间]，初始条件
plot(t, y)
```

#### 9.2 偏微分方程（PDE）求解 [pdf_451]

```matlab
sol = pdepe(m, @pdefun, @icfun, @bcfun, xmesh, tspan)  % 1D 抛物型/椭圆型 PDE
```

**pdepe 求解方程形式** [pdf_10]：
$$c\left(x,t,u,\frac{\partial u}{\partial x}\right)\frac{\partial u}{\partial t} = x^{-m}\frac{\partial}{\partial x}\left[x^m f\left(x,t,u,\frac{\partial u}{\partial x}\right)\right] + s\left(x,t,u,\frac{\partial u}{\partial x}\right)$$

---

### 第十章 基本统计与数据分析 [pdf_452]

#### 10.1 描述性统计 [pdf_452]

```matlab
mx = max(data)          % 最大值
mn = min(data)          % 最小值
mu = mean(data)         % 均值
sigma = std(data)       % 标准差
md = median(data)       % 中位数
pr = prctile(data, p)   % 百分位数
R = corrcoef(data)      % 相关系数矩阵
C = cov(data)           % 协方差矩阵
```

**示例**：对 24×3 矩阵计算统计量 [pdf_452]：
```matlab
load count.dat
mx = max(count)         % 每列最大值
mu = mean(count)        % 每列均值
sigma = std(count)      % 每列标准差
min(count(:))           % 整个矩阵的最小值
```

#### 10.2 随机数生成 [pdf_451]

```matlab
rand(m, n)              % 均匀分布 [0,1]
randn(m, n)             % 标准正态分布 N(0,1)
randi(k, m, n)          % 均匀整数 [1,k]
randperm(n)             % 1~n 的随机排列
```

---

## 第四篇：数据可视化

### 第十一章 二维绘图 [pdf_450][pdf_452]

#### 11.1 基本绘图函数

```matlab
plot(x, y)              % 折线图
plot(x, y, '--rs')      % 虚线-红色-方形标记
plotyy(x1, y1, x2, y2)  % 双 y 轴
semilogx(x, y)          % x 轴对数坐标
semilogy(x, y)          % y 轴对数坐标
loglog(x, y)            % 双对数坐标
stairs(x, y)            % 阶梯图
stem(x, y)              % 火柴杆图
bar(x, y)               % 条形图
histogram(x)            % 直方图
area(x, y)              % 面积图
pie(x)                  % 饼图
scatter(x, y)           % 散点图
```

#### 11.2 图形修饰与标注 [pdf_452]

```matlab
plot(t, count)
legend('Location 1', 'Location 2', 'Location 3', 'Location', 'NorthWest')
xlabel('Time')
ylabel('Vehicle Count')
title('Traffic Counts at Three Intersections')
grid on
axis equal             % 各轴等比例
xlim([0 24])           % 设置 x 轴范围
text(x, y, 'label')    % 添加文本标注
```

#### 11.3 子图

```matlab
subplot(3,1,1)         % 3 行 1 列，第 1 个子图
plot(data(:,1))
subplot(3,1,2)
plot(data(:,2))
subplot(3,1,3)
plot(data(:,3))
```

---

### 第十二章 三维绘图 [pdf_450]

```matlab
plot3(x, y, z)          % 三维曲线
surf(X, Y, Z)           % 曲面图（着色）
mesh(X, Y, Z)           % 网格曲面
contour(X, Y, Z)        % 等高线图
contourf(X, Y, Z)       % 填充等高线
pcolor(X, Y, Z)         % 伪彩色图
colorbar                % 显示色标
view(az, el)            % 设置视角
```

---

## 第五篇：数据导入与导出

### 第十三章 文件 I/O 全面指南 [pdf_454]

#### 13.1 支持的文件格式 [pdf_454]

| 文件内容 | 扩展名 | 导入函数 | 导出函数 |
|----------|--------|:--------:|:--------:|
| MATLAB 工作区 | `.mat` | `load` | `save` |
| 分隔文本数字 | `.csv .txt` | `readmatrix` | `writematrix` |
| 文本+数字混合 | `.csv .txt` | `readtable` | `writetable` |
| 纯文本 | `.txt` | `readlines` | `writelines` |
| 电子表格 | `.xls .xlsx` | `readmatrix` | `writematrix` |
| 图像 | `.jpg .png .tif` | `imread` | `imwrite` |
| 音频 | `.wav .mp3` | `audioread` | `audiowrite` |
| 视频 | `.avi .mp4` | `VideoReader` | `VideoWriter` |
| XML | `.xml` | `xmlread` | `xmlwrite` |
| JSON | `.json` | `jsondecode` | `jsonencode` |

#### 13.2 高级导入函数 [pdf_454]

```matlab
% 以表的形式导入
T = readtable('airlinesmall.csv');
T(1:5, 1:5)          % 显示前 5 行前 5 列

% 自定义导入选项
opts = detectImportOptions('data.txt');
opts.DataLines = [2, Inf];    % 从第 2 行开始
opts.VariableTypes = {'double', 'string', 'double'};
T = readtable('data.txt', opts);
```

#### 13.3 低级文件 I/O [pdf_454]

当高级函数无法满足需求时，使用低级 I/O：

```matlab
fid = fopen(filename, 'r');    % 打开文件
data = fscanf(fid, '%f');      % 格式化读取
line = fgetl(fid);             % 逐行读取
data = fread(fid, count, 'double'); % 二进制读取
fclose(fid);                   % 关闭文件
```

#### 13.4 内存映射大文件 [pdf_454]

```matlab
m = memmapfile('largefile.dat', 'Format', {'double', [1000 1000], 'Data'});
m.Data(1:10, :) = new_data;    % 直接读写部分数据
```

#### 13.5 批量处理文件 [pdf_454]

```matlab
% 批量读取编号文件
numfiles = 20;
mydata = cell(1, numfiles);
for k = 1:numfiles
    myfilename = sprintf('file%d.txt', k);
    mydata{k} = importdata(myfilename);
end

% 批量读取匹配模式的文件
jpegFiles = dir('*.jpg');
for k = 1:length(jpegFiles)
    mydata{k} = imread(jpegFiles(k).name);
end
```

---

## 第六篇：Simulink 仿真基础

### 第十四章 Simulink 入门 [pdf_10]

#### 14.1 启动 Simulink

```matlab
simulink    % 在命令行启动 Simulink Library Browser
```

#### 14.2 Simulink 模块库 [pdf_10]

| 模块库 | 包含模块 |
|--------|----------|
| **Sources（信号源）** | Step, Ramp, Sine Wave, Constant, Pulse Generator, Clock, Random Number |
| **Sinks（接收器）** | Scope, Display, To Workspace, To File, Out1 |
| **Continuous（连续）** | Integrator, Derivative, Transfer Fcn, State-Space |
| **Math Operations（数学运算）** | Gain, Sum, Product, Abs, Trigonometric Function |
| **Discontinuities（不连续）** | Saturation, Dead Zone, Relay |
| **Signal Routing（信号路由）** | Mux, Demux, Switch, Bus Creator |

#### 14.3 Simulink 建模流程

1. 打开 Simulink → 新建空白模型
2. 从 Library Browser 拖入模块
3. 连接模块（按住 Ctrl 拖动连线）
4. 双击模块设置参数
5. 点击 Run 运行仿真
6. 双击 Scope 查看结果

#### 14.4 化工过程仿真示例 [pdf_10]

**液位模型**：
$$\frac{dh}{dt} = \frac{1}{A}(F_0 - \beta\sqrt{h})$$

**MATLAB 代码实现**：
```matlab
% File: odeeq.m
function dh = odeeq(t, h)
    A = 10; beta = 2; F0 = 1;
    dh = (1/A) * (F0 - beta * sqrt(h));
end

% 求解
[t, h] = ode45(@odeeq, [0 100], 0);
plot(t, h)
```

---

## 第七篇：高级主题

### 第十五章 大型数据处理 [pdf_454]

#### 15.1 tall 数组 [pdf_454]

用于处理无法装入内存的大数据：

```matlab
ds = datastore('largefile.csv');
T = tall(ds);
mean_T = mean(T.Var1);     % 立即返回，未计算
result = gather(mean_T);   % 触发计算并收集结果
```

**tall 数组支持**：
- Statistics and Machine Learning Toolbox
- Database Toolbox
- Parallel Computing Toolbox
- MATLAB Parallel Server

#### 15.2 数据存储（Datastore）[pdf_454]

```matlab
ds = datastore('folder/*.csv');  % 创建数据存储
while hasdata(ds)
    data = read(ds);
    process(data);
end
```

---

### 第十六章 网络通信与 JSON [pdf_454]

#### 16.1 TCP/IP 通信

```matlab
t = tcpclient("127.0.0.1", 1045);       % 创建 TCP 连接（替换为实际服务地址）
data = read(t, 30, "double");           % 读取数据
clear t                                 % 关闭连接
```

#### 16.2 JSON 编解码

```matlab
str = jsonencode(data);        % MATLAB 数据 → JSON 字符串
data = jsondecode(str);        % JSON 字符串 → MATLAB 数据
```

---

### 第十七章 HPC 与并行计算（基于上海交大超算）[pdf_455]

#### 17.1 超算平台启动 MATLAB

**命令行交互式** [pdf_455]：
```bash
$ srun -p 64c512g -n 10 --pty /bin/bash
$ module load matlab/r2022b
$ matlab
```

**提交 MATLAB 脚本** [pdf_455]：
```matlab
% 使用 batch 命令提交
batch('my_script')
```

#### 17.2 GPU 版 MATLAB [pdf_455]

1. 在 HPC Studio 选择 GPU 资源（如 1gpu-a100-sy）
2. 启动远程桌面
3. 终端输入 `module load matlab/r2022a && matlab`

#### 17.3 并行计算工具箱

```matlab
parpool('local', 4)          % 开启 4 个 worker 并行池
parfor i = 1:100
    result(i) = heavy_computation(i);
end
gpuArray(rand(1000, 'single'))  % GPU 数组
```

---

### 第十八章 桌面工具与 App 设计 [pdf_409]

#### 18.1 导入工具（Import Tool）

交互式导入数据：**主页 → 导入数据** 或 `uiimport`。

#### 18.2 App Designer

用于构建交互式 GUI 应用程序：
- 拖放式 UI 组件设计
- 代码视图编写回调函数
- 打包为独立桌面应用或 Web App

---

## 第八篇：快速参考

### 附录一：常用函数速查

#### A.1 矩阵操作

| 函数 | 说明 |
|------|------|
| `size(A)` | 矩阵维度 |
| `length(A)` | 最大维度长度 |
| `numel(A)` | 元素总数 |
| `ndims(A)` | 维度数 |
| `reshape(A, m, n)` | 重塑形状 |
| `sort(A)` | 排序 |
| `unique(A)` | 去重 |
| `flip(A)` | 翻转 |
| `rot90(A)` | 旋转 90° |
| `transpose(A)` | 转置 |

#### A.2 数学函数

| 函数 | 说明 |
|------|------|
| `sin/cos/tan` | 三角函数 |
| `asin/acos/atan` | 反三角函数 |
| `exp` | 指数 |
| `log/log10/log2` | 对数 |
| `sqrt` | 平方根 |
| `abs` | 绝对值 |
| `round/floor/ceil/fix` | 取整 |
| `mod/rem` | 取模/余数 |
| `real/imag/conj` | 复数操作 |

#### A.3 线性代数

| 函数 | 说明 |
|------|------|
| `inv(A)` | 矩阵求逆 |
| `det(A)` | 行列式 |
| `eig(A)` | 特征值 |
| `svd(A)` | 奇异值分解 |
| `lu(A)` | LU 分解 |
| `qr(A)` | QR 分解 |
| `chol(A)` | Cholesky 分解 |
| `norm(A)` | 矩阵范数 |
| `cond(A)` | 条件数 |
| `rank(A)` | 秩 |

#### A.4 绘图

| 函数 | 说明 |
|------|------|
| `plot` | 折线图 |
| `bar` | 条形图 |
| `histogram` | 直方图 |
| `scatter` | 散点图 |
| `surf` | 曲面图 |
| `mesh` | 网格图 |
| `contour` | 等高线图 |
| `subplot` | 子图 |
| `hold on/off` | 图形叠加 |
| `figure` | 新建图形窗口 |

#### A.5 文件 I/O

| 函数 | 说明 |
|------|------|
| `load/save` | MAT 文件读写 |
| `readtable/writetable` | 表格数据读写 |
| `readmatrix/writematrix` | 矩阵数据读写 |
| `imread/imwrite` | 图像读写 |
| `audioread/audiowrite` | 音频读写 |
| `fopen/fclose` | 低级文件打开/关闭 |
| `fscanf/fprintf` | 格式化读写 |
| `fread/fwrite` | 二进制读写 |

### 附录二：MATLAB 运算符优先级

| 优先级 | 运算符 |
|:------:|:-------|
| 1 (最高) | 括号 `()` |
| 2 | 转置 `.'` `'`，幂 `^` `.^` |
| 3 | 一元正负号 `+` `-`，逻辑非 `~` |
| 4 | 乘法除法 `.*` `*` `./` `./` `\` `\` |
| 5 | 加法减法 `+` `-` |
| 6 | 冒号 `:` |
| 7 | 关系运算符 `<` `<=` `>` `>=` `==` `~=` |
| 8 | 逻辑与 `&` |
| 9 | 逻辑或 `\|` |
| 10 (最低) | 赋值 `=` |

### 附录三：MATLAB 版本历史与兼容性

| 版本号 | 发布年份 | 关键新特性 |
|:------:|:--------:|-----------|
| R2024a | 2024 | name=value 语法增强 |
| R2023b | 2023 | 改进的实时编辑器 |
| R2021a | 2021 | `name=value` 语法引入 |
| R2016b | 2016 | 隐式扩展（广播） |
| R2015b | 2015 | `live script` 实时脚本 |
| R2013b | 2013 | `table` 数据类型 |
| R2009b | 2009 | 支持并行计算 |
| R2006a | 2006 | 类定义 `classdef` |

---

> **数据来源**：本文档综合知识库中 MATLAB 官方中文文档 `matlab_prog_zh_CN.pdf` (pdf_409)、`learn_matlab_zh_CN.pdf` (pdf_450)、`matlab_math_zh_CN.pdf` (pdf_451)、`data_analysis_zh_CN.pdf` (pdf_452)、`import_export_zh_CN.pdf` (pdf_454)、`hpcmanual.pdf` (pdf_455) 及 `Introduction to Software for Chemical Engineers` (pdf_10) 等内容整理而成。MATLAB 为 MathWorks 公司的注册商标。