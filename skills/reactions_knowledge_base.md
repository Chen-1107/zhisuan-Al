# 知识库反应数据库大全（Reaction_Database）

> 本文档综合知识库中所有教科书与参考资料中记载的有机/无机/生化反应类型与具体反应，涵盖四大反应原型、六大基础反应类型、100+ 人人名反应及无机配位/电子转移反应。

---

## 第一篇：总论——反应原型与分类

### 第一章 四大反应原型（Scudder）[pdf_459]

有机化学中的所有反应可归纳为**四种基本原型**：

| 原型 | 示意图 | 说明 |
|:----:|:------|------|
| **取代 (Substitution)** | `C—L + Nu → C—Nu + L` | 离去基团被亲核试剂取代 |
| **消除 (Elimination)** | `C—C(L,H) → C=C + L + H` | 失去离去基团和质子，形成 π 键 |
| **加成 (Addition)** | `C=C + Nu-E → C—C(Nu,E)` | 亲核试剂和亲电试剂相加到 π 键 |
| **重排 (Rearrangement)** | `C—C—X → C—C—X`（异构化） | 分子内原子重排产生构造异构体 |

#### 1.1 电子流动基本路径 [pdf_459]

| 缩写 | 路径名称 | 说明 |
|:----:|:---------|------|
| p.t. | Proton Transfer | 质子转移至孤对电子 |
| DN | Dissociation, Nucleofugic | 离去基团电离（形成碳正离子） |
| AN | Association, Nucleophilic | 亲核试剂捕获缺电子物种 |
| AE | Addition, Electrophilic | 亲电试剂加到多重键 |
| DE | Electrofuge Loss | 从阳离子失去亲电体形成 π 键 |
| SN2 | Bimolecular Substitution | 协同取代 |
| E2 | Bimolecular Elimination | 协同消除 |
| Eβ | Beta Elimination | 阴离子 β 消除 |
| AdN | Nucleophilic Addition | 亲核加成到极化多重键 |
| 1,2R | 1,2-Rearrangement | 碳正离子 1,2-迁移重排 |

#### 1.2 常见路径组合 [pdf_459]

| 反应类型 | 路径组合 | 说明 |
|:--------:|:--------:|------|
| **SN1** | DN + AN | 单分子亲核取代 |
| **SN2** | SN2 | 双分子亲核取代 |
| **E1** | DN + DE | 单分子消除 |
| **E2** | E2 | 双分子消除 |
| **E1cB** | p.t. + Eβ | 共轭碱单分子消除 |
| **AdE2** | AE + AN | 双分子亲电加成 |
| **AdN2** | AdN + p.t. | 双分子亲核加成 |
| **SE2Ar** | AE + DE | 芳香亲电取代 |
| **Addition-Elimination** | AdN + Eβ | 加成-消除（酰基取代）|
| **Tautomerization** | taut. | 互变异构 |

---

### 第二章 六大有机制基石反应类型（Karty）[pdf_51]

| 类型 | 特征 | 示例 |
|:----:|:-----|:-----|
| **亲核取代** | 亲核试剂取代离去基团 | SN1, SN2 |
| **消除** | 失去离去基团与质子形成 π 键 | E1, E2, E1cB |
| **亲电加成** | 亲电试剂加到非极性 π 键 | 卤素加成、羟汞化、硼氢化、环氧化 |
| **亲核加成** | 亲核试剂加到极性 π 键 | 羰基加成、Grignard、Wittig |
| **亲核加成-消除** | 先加成后消除（酰基取代） | 酯水解、酰胺化、酯交换 |
| **芳香取代** | 芳香环上的取代反应 | SEAr, SNAr, SRN1 |
| **周环反应** | 协同环状过渡态 | Diels-Alder、电环化、σ 迁移 |
| **自由基反应** | 自由基中间体 | 自由基加成、卤化、聚合 |
| **氧化还原** | 电子转移改变氧化态 | 氧化、还原、氢化 |

---

## 第二篇：亲核取代反应（Nucleophilic Substitution）

### 第三章 SN2 反应 [pdf_212][pdf_459][pdf_51]

**全称**：Substitution, Nucleophilic, Bimolecular

**速率方程**：$\text{rate} = k[\text{Nu}][\text{R-L}]$

**机理**：协同过程，一步完成，亲核试剂从背面进攻，离去基团同时离开，构型翻转（Walden 反转）。

**反应式**：$\text{R-L} + \text{Nu}^- \rightarrow \text{R-Nu} + \text{L}^-$

**底物活性**：$\text{CH}_3 > 1^\circ > 2^\circ > 3^\circ$（甲基 > 伯 > 仲 > 叔，空间位阻主导）

**离去基团**：$\text{OTf} > \text{OTs} > \text{OMs} > \text{I} > \text{Br} > \text{Cl} > \text{F}$（$pK_a$ 越小越好）

**亲核试剂**：$\text{RS}^- > \text{I}^- > \text{RO}^- > \text{CN}^- > \text{Br}^- > \text{NH}_3 > \text{Cl}^- > \text{H}_2\text{O}$

**溶剂**：极性非质子溶剂（DMSO, DMF, CH₃CN, acetone）加速反应

**特征**：[pdf_461]
- 二级动力学
- Walden 反转（构型完全翻转）
- 无重排产物
- 底物：伯碳最佳，甲基最快

### 第四章 SN1 反应 [pdf_212][pdf_459][pdf_51]

**全称**：Substitution, Nucleophilic, Unimolecular

**速率方程**：$\text{rate} = k[\text{R-L}]$

**机理**：两步反应：
1. 慢步骤：$\text{R-L} \rightarrow \text{R}^+ + \text{L}^-$（电离，决速步）
2. 快步骤：$\text{R}^+ + \text{Nu}^- \rightarrow \text{R-Nu}$

**反应式**：$\text{R-L} + \text{Nu} \rightarrow \text{R-Nu} + \text{L}$

**底物活性**：$3^\circ > 2^\circ > 1^\circ$（叔 > 仲 > 伯，碳正离子稳定性主导）

**碳正离子稳定性**：$3^\circ > 2^\circ > 1^\circ > \text{CH}_3^+$，烯丙基/苄基碳正离子特别稳定

**特征**：[pdf_461]
- 一级动力学
- 外消旋化（部分翻转 + 部分保留）
- 有重排可能（Wagner-Meerwein）
- 底物：叔碳最佳
- 溶剂：极性**质子**溶剂（水、醇）加速

### 第五章 SN2 与 SN1 对比总结 [pdf_461]

| 比较项目 | SN2 | SN1 |
|:--------|:---:|:---:|
| **动力学** | 二级 | 一级 |
| **机理** | 协同一步 | 两步（碳正离子中间体） |
| **立体化学** | 构型完全翻转 | 外消旋化 |
| **底物** | 甲基 > 伯 > 仲 | 叔 > 仲（伯不反应） |
| **亲核试剂** | 需要强亲核试剂 | 亲核试剂强弱不重要 |
| **离去基团** | 重要 | 重要 |
| **溶剂** | 极性非质子（DMSO, DMF, CH₃CN） | 极性质子（H₂O, ROH） |
| **重排** | 无 | 可能发生 |
| **竞争反应** | E2 竞争 | E1 竞争 |

---

## 第三篇：消除反应（Elimination）

### 第六章 E2 反应 [pdf_459][pdf_51][pdf_212]

**全称**：Elimination, Bimolecular

**速率方程**：$\text{rate} = k[\text{B}][\text{R-L}]$

**机理**：一步协同过程，碱夺取 β-氢，同时离去基团离开，形成 π 键。

**反应式**：$\text{B}^- + \text{H-C-C-L} \rightarrow \text{B-H} + \text{C=C} + \text{L}^-$

**立体化学**：反式共平面（anti-periplanar）消除，产物为反式烯烃

**区域选择性**：Zaitsev 规则——生成更稳定的取代烯烃（更多烷基取代的烯烃为主产物）

**底物活性**：$3^\circ > 2^\circ > 1^\circ$

**碱强度**：强碱（$\text{RO}^-$、$\text{OH}^-$、$\text{NH}_2^-$）促进 E2

**特征**：
- 二级动力学
- 反式消除
- 与 SN2 竞争（强碱但位阻大的碱利于 E2）

### 第七章 E1 反应 [pdf_459][pdf_51]

**全称**：Elimination, Unimolecular

**速率方程**：$\text{rate} = k[\text{R-L}]$

**机理**：两步反应：
1. 慢步骤：$\text{R-L} \rightarrow \text{R}^+ + \text{L}^-$（电离）
2. 快步骤：$\text{B}^- + \text{R}^+ \rightarrow \text{C=C} + \text{B-H}$

**特征**：与 SN1 共享第一步（碳正离子），因此 SN1 和 E1 总是同时发生。

**底物**：$3^\circ$ 最佳，需要稳定的碳正离子

**区域选择性**：Zaitsev 规则

### 第八章 E1cB 反应 [pdf_459]

**全称**：Elimination, Unimolecular, Conjugate Base

**路径**：p.t. + Eβ

**机理**：两步反应：
1. 碱夺取酸性 β-氢，形成碳负离子（共轭碱）
2. 碳负离子 β-消除离去基团

**适用底物**：β-位有强吸电子基团（EWG）使 β-氢酸性增强

**特征**：与 E2 竞争，常在弱碱性条件下发生

### 第九章 消除反应对比 [pdf_461][pdf_51]

| 比较项目 | E2 | E1 | E1cB |
|:--------|:---:|:---:|:----:|
| **动力学** | 二级 | 一级 | 一级 |
| **机理** | 协同一步 | 两步（碳正离子） | 两步（碳负离子） |
| **碱强度** | 强碱 | 弱碱 | 中等碱 |
| **底物** | 伯/仲/叔 | 叔（需稳定碳正离子） | 含 EWG 的底物 |
| **立体化学** | 反式共平面 | 无要求 | 反式共平面 |
| **重排** | 无 | 可能 | 无 |
| **竞争反应** | SN2 | SN1 | 无 |

---

## 第四篇：加成反应（Addition）

### 第十章 亲电加成（AdE2）[pdf_47][pdf_51][pdf_459]

**机理**：AE + AN，亲电试剂先加成到 π 键，然后亲核试剂捕获。

**通用反应**：$\text{C=C} + \text{E-Nu} \rightarrow \text{C-C(E,Nu)}$

#### 10.1 卤素加成 [pdf_47]

**反应**：$\text{C=C} + \text{X}_2 \rightarrow \text{C-C(X,X)}$（X = Br, Cl）

**机理**：经过环状卤鎓离子中间体，**反式加成**。

**特征**：立体专一的反式加成，产物为邻二卤化物。

#### 10.2 氢卤酸加成 [pdf_47]

**反应**：$\text{C=C} + \text{HX} \rightarrow \text{C-C(H,X)}$

**Markovnikov 规则**：H 加成到含氢较多的碳上，X 加成到含氢较少的碳上。

**机理**：经过碳正离子中间体，可能发生重排。

#### 10.3 羟汞化-还原 [pdf_51]

**反应**：$\text{C=C} + \text{Hg(OAc)}_2 + \text{H}_2\text{O} \rightarrow \text{醇}$（NaBH₄ 还原）

**特征**：Markovnikov 加成，无重排，**顺式**加成。

#### 10.4 硼氢化-氧化 [pdf_51]

**反应**：$\text{C=C} + \text{BH}_3 \rightarrow \text{烷基硼} \xrightarrow{\text{H}_2\text{O}_2/\text{OH}^-} \text{醇}$

**特征**：**反-Markovnikov** 加成，**顺式**加成，无重排。

#### 10.5 环氧化 [pdf_51]

**反应**：$\text{C=C} + \text{RCO}_3\text{H} \rightarrow \text{环氧化物}$

**特征**：经过环状过渡态，立体专一的顺式加成。

### 第十一章 亲核加成（AdN）[pdf_51][pdf_459]

**通用反应**：$\text{C=O} + \text{Nu}^- \rightarrow \text{C-O(Nu)}$

**机理**：AdN + p.t.，亲核试剂加到羰基碳，然后质子化。

#### 11.1 羰基亲核加成通式 [pdf_51]

$$\text{R}_2\text{C=O} + \text{Nu}^- \xrightarrow{\text{AdN}} \text{R}_2\text{C(Nu)O}^- \xrightarrow{\text{p.t.}} \text{R}_2\text{C(Nu)OH}$$

#### 11.2 Grignard 反应 [pdf_51]

**反应**：$\text{RMgX} + \text{C=O} \rightarrow \text{醇}$

| 羰基底物 | 产物 |
|:---------|:----:|
| 甲醛 + R'MgX | 伯醇 |
| 醛 + R'MgX | 仲醇 |
| 酮 + R'MgX | 叔醇 |
| 酯 + 2 R'MgX | 叔醇 |
| CO₂ + R'MgX | 羧酸 |

#### 11.3 氢化物还原 [pdf_51]

| 还原剂 | 选择性 | 说明 |
|:------|:-------|:-----|
| NaBH₄ | 醛酮还原为醇 | 温和，不还原酯/羧酸 |
| LiAlH₄ | 醛酮酯羧酸→醇 | 强还原剂，还原范围广 |
| DIBAH | 酯→醛（-78°C） | 选择性还原酯为醛 |
| LTBA | 选择性还原 | 位阻大的还原剂 |

#### 11.4 Wittig 反应 [pdf_51][pdf_212]

**反应**：$\text{R}_2\text{C=O} + \text{Ph}_3\text{P=CR'}_2 \rightarrow \text{R}_2\text{C=CR'}_2 + \text{Ph}_3\text{PO}$

**机理**：叶立德（ylide）与羰基加成 → 四元环中间体（oxaphosphetane）→ 消除 Ph₃PO

**特征**：将 C=O 转化为 C=C，是合成烯烃的核心方法。

#### 11.5 1,2-与 1,4-加成（Michael 加成）[pdf_459]

**α,β-不饱和羰基**的两种加成模式：
- **1,2-加成**（直接加成）：硬亲核试剂（RLi, RMgX）攻击羰基碳
- **1,4-加成**（共轭加成）：软亲核试剂（R₂CuLi, 硫醇）攻击 β-碳

---

## 第五篇：亲核加成-消除（酰基取代）

### 第十二章 酰基亲核取代 [pdf_51][pdf_459][pdf_47]

**机理**：AdN + Eβ（加成-消除两步）

**通式**：$\text{R-C(=O)-L} + \text{Nu} \rightarrow \text{R-C(=O)-Nu} + \text{L}$

**羰基衍生物反应活性顺序**：
$$\text{RCOCl} > \text{RCO}_2\text{R'} > \text{RCO}_2\text{H} > \text{RCONH}_2 > \text{RCO}_2^-$$

**常见反应** [pdf_51]：

| 反应 | 底物 | 亲核试剂 | 产物 |
|:----|:-----|:---------|:----:|
| 酯水解 | 酯 | H₂O (H⁺/OH⁻) | 羧酸 |
| 酯交换 | 酯 | R'OH | 新酯 |
| 皂化 | 酯 | OH⁻ | 羧酸盐 |
| 酰胺化 | 酯/酰氯 | NH₃/RNH₂ | 酰胺 |
| 酰氯醇解 | 酰氯 | ROH | 酯 |
| 酸酐氨解 | 酸酐 | NH₃ | 酰胺 |
| Gabriel 合成 | 邻苯二甲酰亚胺 | 烷基卤 | 伯胺 |
| Haloform 反应 | 甲基酮 | X₂/OH⁻ | 羧酸 + CHX₃ |

---

## 第六篇：芳香取代反应

### 第十三章 亲电芳香取代（SEAr）[pdf_459][pdf_47][pdf_143][pdf_212]

**机理**：AE + DE（亲电加成 + 质子消除）

**步骤**：亲电试剂攻击芳香环 → σ-配合物（Wheland 中间体/Mesenheimer 复合物）→ 失去质子恢复芳香性

**速率方程**：$\text{rate} = k[\text{ArH}][\text{E}^+]$

**常见 SEAr 反应** [pdf_143]：

| 反应类型 | 试剂 | 亲电物种 | 产物 |
|:---------|:-----|:---------|:----:|
| **硝化** | HNO₃/H₂SO₄ | NO₂⁺ | 硝基芳烃 |
| **卤化** | Br₂/FeBr₃, Cl₂/FeCl₃ | Br⁺, Cl⁺ | 卤代芳烃 |
| **磺化** | H₂SO₄/SO₃ | SO₃H⁺ | 磺酸 |
| **Friedel-Crafts 烷基化** | RCl/AlCl₃ | R⁺ | 烷基苯 |
| **Friedel-Crafts 酰基化** | RCOCl/AlCl₃ | RCO⁺ | 芳酮 |

**取代基定位效应** [pdf_143]：

| 定位类型 | 取代基 | 性质 |
|:---------|:-------|:-----|
| **邻对位定位** | -OH, -NH₂, -OR, -R, -Ph | 活化（给电子基） |
| **间位定位** | -NO₂, -CN, -SO₃H, -CO₂H, -CF₃ | 钝化（吸电子基） |
| **邻对位定位（钝化）** | -F, -Cl, -Br, -I | 钝化但邻对位 |

### 第十四章 亲核芳香取代（SNAr）[pdf_212]

**机理**：加成-消除（经过 Meisenheimer 复合物）

**特征**：需要强吸电子基（EWG：NO₂, CF₃, SO₂R, CN, N₂⁺）活化芳香环

**底物要求**：EWG 必须位于离去基团的**邻位或对位**

**速率方程**：$\text{rate} = k[\text{ArX}][\text{Nu}^-]$

### 第十五章 芳香自由基亲核取代（SRN1）[pdf_212]

**机理**：自由基链反应，通过 SET（单电子转移）

**特征**：需要光照或引发剂，底物不需要 EWG 活化

---

## 第七篇：周环反应（Pericyclic Reactions）

### 第十六章 Diels-Alder 反应 [pdf_185][pdf_459][pdf_213][pdf_51]

**类型**：[4+2] 环加成反应

**通式**：共轭二烯（4π）+ 亲二烯体（2π）→ 六元环

**机理**：协同反应，经过环状芳香过渡态（Hückel 体系）

**特征**：
- 立体专一：顺式-顺式二烯 + 顺式亲二烯体 → 顺式产物
- Alder 内型规则（endo rule）：endo 过渡态更稳定
- 热反应，受热驱动

**木香烃规则（Woodward-Hoffmann 规则）** [pdf_185]：
- 热允许：[4+2] 体系中，二烯以 s-顺式构象反应
- 前线分子轨道（FMO）理论：二烯 HOMO 与亲二烯体 LUMO 相互作用

**Ozonolysis（臭氧分解）** [pdf_458]：最重要的[4+2] 偶极环加成反应，机理涉及 [4+2] 和 retro-[4+2] 步骤。

### 第十七章 [3+2] 环加成（Huisgen 反应）[pdf_213]

**通式**：1,3-偶极体（3π）+ 亲偶极体（2π）→ 五元环杂环

**经典例子**：叠氮 + 炔烃 → 1,2,3-三唑（点击化学）

### 第十八章 电环化反应 [pdf_462][pdf_459]

**机理**：共轭 π 体系通过环状过渡态形成新的 σ 键

**Woodward-Hoffmann 规则** [pdf_462]：

| 电子数 | 热反应 | 光化学反应 |
|:------:|:------:|:----------:|
| $4n$ | 对旋（conrotatory） | 顺旋（disrotatory） |
| $4n+2$ | 顺旋（disrotatory） | 对旋（conrotatory） |

### 第十九章 σ 迁移重排 [pdf_212][pdf_459]

| 重排类型 | 反应 | 说明 |
|:---------|:-----|:------|
| **[3,3]-σ 迁移** | Cope 重排 | 1,5-二烯重排 |
| | Oxy-Cope 重排 | 羟基取代的 Cope 重排，加速 |
| | Aza-Cope 重排 | 含氮的 Cope 重排 |
| | Claisen 重排 | 烯丙基芳基醚→邻烯丙基酚 |
| **[1,5]-σ 迁移** | H 迁移 | 氢在 π 体系中的 1,5-迁移 |
| **[2,3]-σ 迁移** | 叶立德重排 | 硫/氮叶立德重排 |

---

## 第八篇：过渡金属催化交叉偶联

### 第二十章 交叉偶联反应基本机理 [pdf_122]

**通用催化循环**（以 Pd 为例）：

$$\text{Pd(0)} \xrightarrow{\text{氧化加成}} \text{Pd(II)} \xrightarrow{\text{转金属化}} \text{Pd(II)(Ar)(R)} \xrightarrow{\text{还原消除}} \text{Ar-R} + \text{Pd(0)}$$

**催化循环三步** [pdf_122]：
1. **氧化加成（Oxidative Addition）**：Ar-X + Pd(0) → Ar-Pd(II)-X
2. **转金属化（Transmetalation）**：Ar-Pd(II)-X + R-M → Ar-Pd(II)-R + M-X
3. **还原消除（Reductive Elimination）**：Ar-Pd(II)-R → Ar-R + Pd(0)

**选择性来源** [pdf_122]：芳基亲电体（Ar-X）进行氧化加成，芳基亲核体（Ar-Y, Y = B, Zn, Sn, Si 等）进行转金属化，二者不交叉，确保高选择性。

### 第二十一章 各类交叉偶联反应 [pdf_212]

| 反应 | 时间 | 诺贝尔奖 | 亲核试剂 | 通式 |
|:----|:----:|:--------:|:--------:|:----:|
| **Heck 偶联** | 1968 | 2010 (Heck) | 烯烃 | Ar-X + 烯烃 → 取代烯烃 |
| **Negishi 偶联** | 1977 | 2010 (Negishi) | ZnR | Ar-X + ZnR → Ar-R |
| **Suzuki 偶联** | 1979 | 2010 (Suzuki) | B(OR)₂ | Ar-X + Ar'-B(OH)₂ → Ar-Ar' |
| **Sonogashira 偶联** | 1975 | — | 炔烃 | Ar-X + 炔烃 → Ar-炔烃 |
| **Stille 偶联** | 1977 | — | SnR₃ | Ar-X + SnR₃ → Ar-R |
| **Kumada 偶联** | 1972 | — | MgX | Ar-X + MgX-Ar' → Ar-Ar' |
| **Hiyama 偶联** | 1988 | — | SiR₃ | Ar-X + SiR₃ → Ar-R |
| **Ullmann 偶联** | 1901 | — | Cu | 2 Ar-X → Ar-Ar |
| **Buchwald-Hartwig 胺化** | 1995 | — | 胺 | Ar-X + R₂NH → Ar-NR₂ |

#### 21.1 Suzuki 偶联 [pdf_212][pdf_124]

**通式**：$\text{Ar-X} + \text{Ar'-B(OH)}_2 \xrightarrow{\text{Pd(0), 碱}} \text{Ar-Ar'}$

**特点**：官能团耐受性高，硼酸稳定，毒性低，可工业化。

**Pd 催化剂**：Pd(PPh₃)₄, Pd(dppf)Cl₂, Pd(OAc)₂/SPhos

**碱**：K₂CO₃, Na₂CO₃, KOH, KOAc

#### 21.2 Negishi 偶联 [pdf_212][pdf_124]

**通式**：$\text{Ar-X} + \text{R-ZnX} \xrightarrow{\text{Pd/Ni}} \text{Ar-R}$

**特点**：有机锌试剂活性高，官能团耐受性好。

**催化体系**：Pd(0) 或 Ni(0) 均可催化。

#### 21.3 Heck 偶联 [pdf_212]

**通式**：$\text{Ar-X} + \text{C=C} \xrightarrow{\text{Pd(0)}} \text{Ar-C=C}$

**特征**：不需要转金属化，烯烃直接插入 Pd-Ar 键（迁移插入），然后 β-H 消除。

---

## 第九篇：重排反应（Rearrangements）

### 第二十二章 1,2-迁移重排 [pdf_459][pdf_212]

| 反应 | 类型 | 迁移基团 | 产物 |
|:----|:----|:---------|:-----|
| **Wagner-Meerwein** | 1,2-烷基/氢迁移 | H, 烷基 | 重排碳正离子产物 |
| **Pinacol-Pinacolone** | 1,2-烷基迁移 | 烷基 | 邻二醇→羰基化合物 |
| **Beckmann** | 肟重排 | R 迁移到 N | 酰胺 |
| **Favorskii** | α-卤酮重排 | R 迁移 | 羧酸/酯 |
| **Benzilic Acid** | 1,2-二酮重排 | R 迁移 | α-羟基酸 |
| **Wolff** | α-重氮酮重排 | R 迁移 | 烯酮（→Arndt-Eistert） |
| **Curtius** | 酰基叠氮重排 | R 迁移到 N | 异氰酸酯→胺 |
| **Hofmann** | 酰胺重排 | R 迁移到 N | 异氰酸酯→胺 |
| **Lossen** | 羟肟酸重排 | R 迁移到 N | 异氰酸酯→胺 |
| **Schmidt** | 羧酸+叠氮酸 | R 迁移到 N | 胺 |

### 第二十三章 其他重要重排反应 [pdf_212]

| 反应 | 描述 |
|:----|:------|
| **Cope 重排** | 1,5-二烯的 [3,3]-σ 迁移重排 |
| **Claisen 重排** | 烯丙基芳基醚→邻烯丙基酚 |
| **Oxy-Cope 重排** | Cope 重排的加速版本（OH 取代） |
| **Aza-Cope 重排** | 含氮的 Cope 重排 |
| **Pummerer 重排** | 亚砜→α-酰氧基硫醚 |
| **Ramberg-Bäcklund 重排** | 砜在碱作用下→烯烃 |
| **Fischer 吲哚合成** | 苯腙→吲哚（[3,3]-σ 迁移 + 环化）|

---

## 第十篇：氧化与还原反应

### 第二十四章 氧化反应 [pdf_212][pdf_47][pdf_213]

| 反应 | 氧化剂 | 底物 | 产物 |
|:----|:------|:-----|:-----|
| **Baeyer-Villiger 氧化** | RCO₃H, m-CPBA | 酮 | 酯 |
| **Swern 氧化** | (COCl)₂/DMSO/Et₃N | 伯/仲醇 | 醛/酮 |
| **Wacker 氧化** | PdCl₂/CuCl₂, O₂ | 烯烃 | 醛/酮 |
| **Upjohn 二羟基化** | OsO₄/NMO | 烯烃 | 邻二醇（顺式）|
| **Prilezhaev 环氧化** | RCO₃H | 烯烃 | 环氧化物 |
| **Wohl-Ziegler 反应** | NBS/hv | 烯丙基/苄基 C-H | 烯丙基/苄基溴 |
| **Norrish I 型** | hv（光解） | 醛酮 | α-裂解产物 |
| **Norrish II 型** | hv（光解） | 酮 | 1,4-双自由基→裂解 |
| **Jones 氧化** | H₂CrO₄ | 伯醇 | 羧酸 |
| **Ozonolysis** | O₃, 后处理 | 烯烃 | 羰基化合物 |

### 第二十五章 还原反应 [pdf_212][pdf_51]

| 反应 | 还原剂 | 底物 | 产物 |
|:----|:------|:-----|:-----|
| **Wolff-Kishner 还原** | NH₂NH₂, KOH/Δ | C=O | CH₂ |
| **Clemmensen 还原** | Zn/Hg, HCl | C=O | CH₂ |
| **Rosenmund 还原** | H₂/Pd-BaSO₄ | RCOCl | RCHO |
| **Birch 还原** | Na/NH₃(l), ROH | 芳香环 | 1,4-环己二烯 |
| **催化氢化** | H₂/Pd, Pt, Ni, Rh | C=C, C≡C | 烷烃 |
| **NaBH₄ 还原** | NaBH₄ | 醛酮 | 醇 |
| **LiAlH₄ 还原** | LiAlH₄ | 醛酮酯羧酸 | 醇 |

---

## 第十一篇：缩合与碳-碳键形成反应

### 第二十六章 Aldol 反应 [pdf_47]

**Aldol 加成**：两分子羰基化合物在碱催化下形成 β-羟基羰基化合物。

**Aldol 缩合**：β-羟基醛酮进一步脱水形成 α,β-不饱和羰基化合物。

**类型**：
- 碱催化 Aldol 反应
- 酸催化 Aldol 缩合
- Crossed Aldol（交叉）——需控制条件
- Directed Aldol（导向）——烯醇硅醚 + Lewis 酸
- 分子内 Aldol（Robinson 成环）

### 第二十七章 Claisen 缩合 [pdf_47]

**反应**：两分子酯在强碱作用下形成 β-酮酯。

**Dieckmann 缩合**：分子内 Claisen 缩合，形成环状 β-酮酯。

### 第二十八章 其他 C-C 键形成反应 [pdf_212][pdf_47][pdf_51]

| 反应 | 试剂 | 产物 |
|:----|:-----|:-----|
| **Knoevenagel 缩合** | 醛 + 含 α-H 的酯/腈（胺催化） | 不饱和酯/腈 |
| **Michael 加成** | 烯醇盐 + α,β-不饱和羰基 | 1,5-二羰基化合物 |
| **Mannich 反应** | 醛/酮 + 胺 + CH₂O | β-氨基羰基化合物 |
| **Robinson 成环** | Michael 加成 + 分子内 Aldol | 环己烯酮 |
| **Reformatsky 反应** | 醛/酮 + α-溴酯/Zn | β-羟基酯 |
| **Grignard 反应** | 醛/酮 + RMgX | 醇 |
| **Wittig 反应** | 醛/酮 + 叶立德 | 烯烃 |
| **Acyloin 缩合** | 酯 + Na | α-羟基酮 |
| **Arndt-Eistert 合成** | 酰氯 + CH₂N₂ → Wolff 重排 | 同系羧酸 |

---

## 第十二篇：自由基反应

### 第二十九章 自由基反应 [pdf_51][pdf_212]

**特征**：自由基链反应，涉及自由基中间体。

**链反应三阶段**：引发（initiation）→ 传递（propagation）→ 终止（termination）

**常见自由基反应**：

| 反应 | 条件 | 产物 |
|:----|:-----|:-----|
| 自由基卤化 | X₂/hv 或 Δ | 卤代烷 |
| 烯丙基溴化（Wohl-Ziegler） | NBS/hv | 烯丙基溴 |
| HBr 自由基加成 | HBr/过氧化物 | 反-Markovnikov 产物 |
| 自由基聚合 | 引发剂/hv/Δ | 聚合物 |
| SRN1 | 芳香卤化物 + Nu⁻/hv | 取代芳烃 |
| Norrish 光解 | hv | 光解产物 |

---

## 第十三篇：无机反应

### 第三十章 配位化合物反应 [pdf_33]

#### 30.1 配体取代反应

**通式**：$\text{[ML}_n] + \text{L}' \rightarrow \text{[ML}_{n-1}\text{L}'] + \text{L}$

**机理** [pdf_33]：

| 机理 | 速率方程 | 特征 |
|:----|:---------|:-----|
| **解离（D）** | $k[\text{ML}_n]$ | 先解离，后结合，类似 SN1 |
| **缔合（A）** | $k[\text{ML}_n][\text{L}']$ | 先结合，后解离，类似 SN2 |
| **交换（I）** | 中间机制 | 协同交换 |

**影响配体取代速率的因素**：金属离子电荷、半径、配体场稳定化能（LFSE）、晶体场活化能（CFAE）

#### 30.2 氧化加成与还原消除

**氧化加成**：$\text{M}^{n} + \text{A-B} \rightarrow \text{M}^{(n+2)}\text{(A)(B)}$

**还原消除**：$\text{M}^{(n+2)}\text{(A)(B)} \rightarrow \text{M}^{n} + \text{A-B}$

**特征**：金属氧化态变化 ±2，配位数变化 ±2，立体化学保持。

#### 30.3 电子转移反应 [pdf_33]

| 类型 | 特征 | 示例 |
|:----|:------|:-----|
| **内层电子转移** | 通过桥连配体 | $\text{[Co(NH}_3)_5\text{Cl]}^{2+} + \text{[Cr(H}_2\text{O)}_6]^{2+}$ |
| **外层电子转移** | 直接电子跳跃 | $\text{[Fe(CN)}_6]^{4-} + \text{[Fe(CN)}_6]^{3-}$ |

**Marcus 理论**：电子转移速率的理论框架，包含重组能项。

---

## 第十四篇：生化反应

### 第三十一章 酶催化反应 [pdf_460]

**Michaelis-Menten 动力学**：
$$v = \frac{V_{\max}[S]}{K_m + [S]}$$

**酶催化特征** [pdf_460]：
- 酶通过结合过渡态降低活化能（Pauling 原理）
- 过渡态寿命约 $10^{-15}$ s（飞秒级）
- 底物 + 酶 → 过渡态复合物 → 产物 + 酶

**Monod 方程（微生物生长）**：$\mu = \mu_{\max}\frac{S}{K_s + S}$

---

## 第十五篇：反应类型索引

### 附录一：反应类型速查表

| 大类 | 反应类型 | 特征机理 | 关键试剂 | 典型产物 |
|:----|:---------|:--------:|:---------|:---------|
| **取代** | SN2 | 协同，反转 | 强 Nu，极性非质子溶剂 | 取代产物 |
| | SN1 | 碳正离子，外消旋 | 弱 Nu，极性质子溶剂 | 取代产物+重排 |
| | SEAr | AE+DE | E⁺，Lewis 酸 | 取代芳烃 |
| | SNAr | 加成-消除 | EWG 活化，强 Nu | 取代芳烃 |
| **消除** | E2 | 协同，反式消除 | 强碱 | 烯烃 |
| | E1 | 碳正离子 | 弱碱，酸 | 烯烃 |
| | E1cB | 碳负离子 | EWG 底物 | 烯烃 |
| **加成** | 亲电加成 | AE+AN | X₂, HX, H₂O | 邻二取代烷烃 |
| | 亲核加成 | AdN+p.t. | Nu⁻, RMgX, NaBH₄ | 醇 |
| | 1,4-加成 | 共轭加成 | 软 Nu, R₂CuLi | 1,4-加成产物 |
| **周环** | Diels-Alder | [4+2] 协同 | 二烯+亲二烯体 | 六元环 |
| | 电环化 | 电环关闭 | hv 或 Δ | 环状产物 |
| | σ-迁移 | 重排 | Δ | 异构体 |
| **偶联** | Heck | Pd 催化 | 烯烃, Ar-X | 取代烯烃 |
| | Suzuki | Pd 催化 | 硼酸, Ar-X | 联芳 |
| | Negishi | Pd/Ni 催化 | 有机锌, Ar-X | 联芳/烷基芳烃 |
| | Sonogashira | Pd/Cu 催化 | 炔烃, Ar-X | 芳基炔烃 |
| | Stille | Pd 催化 | 有机锡, Ar-X | 联芳 |
| **重排** | 1,2-迁移 | 碳正离子/R 迁移 | 酸/Brønsted 酸 | 异构体 |
| | [3,3]-σ | 协同 | Δ | 异构体 |
| **氧化** | 各类 | 多种 | O₃, RCO₃H, CrO₃, OsO₄ | 氧化产物 |
| **还原** | 各类 | 多种 | H₂, NaBH₄, LiAlH₄ | 还原产物 |
| **缩合** | Aldol | 碱催化 | 醛/酮 | β-羟基羰基/不饱和羰基 |
| | Claisen | 强碱 | 酯 | β-酮酯 |
| **无机** | 配体取代 | D/A/I | 配体 | 新配合物 |
| | 电子转移 | 内层/外层 | 氧化剂/还原剂 | 氧化态变化 |
| **生化** | 酶催化 | 过渡态稳定 | 酶 | 酶促产物 |

### 附录二：按试剂/条件索引

| 试剂/条件 | 常见反应 |
|:----------|:---------|
| **H₂/Pd, Pt, Ni** | 催化氢化（C=C, C≡C 还原） |
| **NaBH₄** | 醛酮还原为醇 |
| **LiAlH₄** | 醛酮酯羧酸还原为醇 |
| **RMgX** | Grignard 反应（亲核加成） |
| **R₂CuLi** | 1,4-共轭加成 |
| **Ph₃P=CHR** | Wittig 反应（C=O→C=C） |
| **m-CPBA** | 环氧化，Baeyer-Villiger 氧化 |
| **OsO₄/NMO** | Upjohn 二羟基化（顺式邻二醇） |
| **O₃** | Ozonolysis（烯烃裂解） |
| **NBS/hv** | Wohl-Ziegler 烯丙基溴化 |
| **Pd(PPh₃)₄** | 交叉偶联（Suzuki, Negishi, Heck） |
| **AlCl₃** | Friedel-Crafts 烷基化/酰基化 |
| **HNO₃/H₂SO₄** | 硝化（SEAr） |
| **Br₂/FeBr₃** | 溴化（SEAr） |
| **NH₂NH₂/KOH** | Wolff-Kishner 还原 |
| **Zn(Hg)/HCl** | Clemmensen 还原 |
| **Na/NH₃(l)** | Birch 还原 |
| **hv（光）** | Norrish I/II 型，电环化，自由基反应 |
| **Δ（热）** | Diels-Alder, Cope, Claisen 重排，电环化 |

### 附录三：诺贝尔奖反应一览

| 年份 | 获奖者 | 反应/领域 | 来源 |
|:----:|:-------|:----------|:----:|
| 1912 | Grignard | Grignard 反应 | pdf_47 |
| 1950 | Diels, Alder | Diels-Alder 反应 | pdf_185 |
| 1963 | Ziegler, Natta | Ziegler-Natta 聚合 | pdf_51 |
| 1979 | Brown, Wittig | 硼氢化 / Wittig 反应 | pdf_51 |
| 2001 | Knowles, Noyori, Sharpless | 不对称催化氢化/氧化 | pdf_51 |
| 2005 | Chauvin, Grubbs, Schrock | 烯烃复分解 | pdf_212 |
| 2010 | Heck, Negishi, Suzuki | 钯催化交叉偶联 | pdf_212, pdf_122 |
| 2016 | Stoddart, Sauvage, Feringa | 分子机器 | pdf_212 |
| 2021 | List, MacMillan | 不对称有机催化 | pdf_212 |

---

> **数据来源**：本文档综合知识库中以下资料整理而成：
> - Organic Chemistry Principles and Mechanisms 3e (Karty) [pdf_51]
> - Organic Chemistry 100 Must-Know Mechanisms 2e (Valiulin) [pdf_212]
> - Electron Flow in Organic Chemistry 3e (Scudder) [pdf_459]
> - Organic Chemistry 6e (Loudon & Parise) [pdf_47]
> - Organic Chemistry Workbook (Vogel & Houk) [pdf_213]
> - The Art of Problem Solving in Organic Chemistry 3e (Alonso-Amelot) [pdf_129]
> - Principles of Inorganic Chemistry 2024 (Jordan) [pdf_33]
> - Modern Physical Organic Chemistry (Anslyn) [pdf_414]
> - Computational Organic Chemistry 2e (Bachrach) [pdf_35]
> - Handbook for Chemical Process Research and Development 2e [pdf_124]
> - MIT 5.07SC 生物化学讲义 [pdf_460]
> - MIT 5.111 / 3.091 等讲义中的反应描述（限于反应类型本身）
> - 各类 MIT 有机化学讲义 [pdf_458, pdf_461, pdf_462, pdf_143, pdf_185]
> - s41586-026-10486-8 交叉偶联综述 [pdf_122]
> - Oxford Dictionary of Chemistry 6e [pdf_214]
> 
> 各教科书与资料的版权归原作者及出版社所有。