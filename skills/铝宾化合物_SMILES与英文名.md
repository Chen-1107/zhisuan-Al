# 铝宾（Aluminylenes）文献化合物——SMILES码与英文名对照表

> **说明**：本文档整理了我之前生成的《铝宾（Aluminylenes）文献综述》中提及的主要化合物，逐一给出其英文名（IUPAC名/文献常用名）和 SMILES 码（简化分子线性输入规范）。对于结构极其复杂的有机金属大分子，SMILES 无法完整表达配位模式和电子结构，已附注说明。部分结构基于文献报道推断，以 ⚠️ 标注。

---

## 一、经典 Al(I) 参考化合物

### 1. (Cp\*Al)₄ —— 五甲基环戊二烯基铝(I) 四聚体

| 项目 | 内容 |
|:----|:------|
| **英文名** | Tetrakis(η⁵-pentamethylcyclopentadienylaluminum(I)) |
| **SMILES (单体)** | `CC1=C(C)[C](C)=C(C)[C]1[Al]` |
| **备注** | 四聚体通过 Al–Al 键连接，SMILES 仅表示单体形式 η⁵-C₅Me₅–Al(I)；配位模式为 η⁵，标准 SMILES 无法准确表达。 |

**文献**：Schnöckel, 1991 [pdf_1]

---

### 2. [HC(CMeNDipp)₂]Al —— Roesky 型 Al(I) 化合物

| 项目 | 内容 |
|:----|:------|
| **英文名** | 2,6-Bis[2,6-diisopropylphenyl)imino]pent-2-en-4-ylaluminum(I) |
| **IUPAC名** | 2-((2,6-Diisopropylphenyl)imino)pent-2-en-4-ylidene-2,6-diisopropylphenylamine aluminum(I) |
| **SMILES** | `CC1=C(C)[Al]N(C(C)=C1)C2=C(C=CC=C2C(C)C)C(C)C.N(C3=C(C=CC=C3C(C)C)C(C)C)=C(C)C` |
| **缩写** | Dipp = 2,6-ⁱPr₂C₆H₃（2,6-二异丙基苯基） |
| **备注** | β-二亚胺配体（Nacnac）稳定的 Al(I) 化合物，Al 中心为二配位。 |

**文献**：Roesky, 2000 [pdf_1]

---

### 3. Arⁱᵖʳ⁸–Al —— Power & Tuononen 的自由铝烯

| 项目 | 内容 |
|:----|:------|
| **英文名** | (2,6-Bis(2,4,6-triisopropylphenyl)-3,5-diisopropylphenyl)aluminylene |
| **Arⁱᵖʳ⁸ 结构** | C₆H-2,6-(C₆H₂-2,4,6-ⁱPr₃)₂-3,5-ⁱPr₂ |
| **SMILES** | ⚠️ 结构过于庞大，无法用一行 SMILES 准确表达。核心骨架为：`[Al]c1c(cc(c(c1C(C)C)C(C)C)-c2c(cc(c(c2)C(C)C)C(C)C)C(C)C)C(C)C`（简化表示，仅含核心取代基模式） |
| **备注** | 首个稳定单配位自由铝烯（2020）。Arⁱᵖʳ⁸ 为超大位阻三联苯基配体，为 Al(I) 中心提供动力学保护。 |

**文献**：Power & Tuononen, 2020 [pdf_1]

---

## 二、咔唑基铝烯（核心化合物）

### 4. 化合物 1 —— [N]–Al 咔唑基铝烯

| 项目 | 内容 |
|:----|:------|
| **英文名** | 3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-ylaluminylene |
| **英文缩写** | [N]–Al, carbazolyl-aluminylene |
| **配体 [N] 结构** | 3,6-di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl |
| **SMILES** | ⚠️ 分子量极大（~30个芳环+4个叔丁基），标准 SMILES 过长且无法反映 Al(I) 的电子结构。**简化表示**：咔唑骨架 `c1ccc2c([nH]c3ccccc23)c1`，3,6-位接 `C(C)(C)C`，1,8-位接 `c1cc(cc(c1)C(C)(C)C)C(C)(C)C`，9-位 N 接 `[Al]`。 |
| **分子式** | C₅₆H₇₄AlN（计算值） |
| **产率** | 67%（白色粉末） |
| **Al–N 键长** | 1.913(9) Å |

**文献**：张欣 & 刘柳, 2021; Hinz, 2021 [pdf_1]

---

### 5. 化合物 2 —— [N]–Al(ⁱPr₂-bimy)=Se 中性铝硒化物

| 项目 | 内容 |
|:----|:------|
| **英文名** | [1,3-Diisopropylbenzimidazol-2-ylidene](3,6-di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)aluminum selenide |
| **英文缩写** | [N]–Al(ⁱPr₂-bimy)=Se |
| **SMILES** | ⚠️ 配体 [N] 部分同上；ⁱPr₂-bimy（1,3-二异丙基苯并咪唑-2-亚基）的简化 SMILES：`CC(C)n1c([Al])nc2c1cccc2C(C)C`。整体分子过大，无法用单行 SMILES 准确表达。 |
| **结构特征** | Al 三配位畸变三角平面；**Al–Se 键长 = 2.1935(8) Å**（迄今最短 Al–Se 键） |
| **产率** | 94% |
| **WBI (Al–Se)** | 1.31 |

**文献**：张欣 & 刘柳, *JACS* 2023 [pdf_1][pdf_3]

---

### 6. 化合物 3 —— [N]–Al(ⁱPr₂-bimy)=Te 中性铝碲化物

| 项目 | 内容 |
|:----|:------|
| **英文名** | [1,3-Diisopropylbenzimidazol-2-ylidene](3,6-di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)aluminum telluride |
| **英文缩写** | [N]–Al(ⁱPr₂-bimy)=Te |
| **SMILES** | ⚠️ 同化合物 2，仅将 Se 替换为 Te。 |
| **结构特征** | **Al–Te 键长 = 2.399(1) Å**（迄今最短 Al–Te 键） |
| **产率** | 93% |
| **WBI (Al–Te)** | 1.47 |

**文献**：张欣 & 刘柳, *JACS* 2023 [pdf_1][pdf_3]

---

### 7. 化合物 8 —— ⁱPr₂-bimy 稳定的铝烯

| 项目 | 内容 |
|:----|:------|
| **英文名** | [1,3-Diisopropylbenzimidazol-2-ylidene](3,6-di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)aluminylene |
| **英文缩写** | [N]–Al(ⁱPr₂-bimy) |
| **SMILES** | ⚠️ 同化合物 2/3 的骨架，无末端 Ch 原子。 |
| **结构特征** | Al–C(卡宾) 键长 = 2.244(2) Å；C–Al–N 键角 = 97.66(6)° |
| **备注** | 卡宾配位显著增强了铝烯的氧化还原反应性，可催化芳烃去芳构化。 |

**文献**：张欣 & 刘柳, *Angew. Chem.* 2022 [pdf_1]

---

### 8. 化合物 2–4（过渡金属配合物）

| 编号 | 英文名 | 缩写 | SMILES |
|:---:|:------|:----|:-------|
| **2** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(pentacarbonyltungsten)aluminylene | [N]–Al→W(CO)₅ | ⚠️ 大分子，Al→W 配位键 |
| **3** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(tetrahydrofuran)(pentacarbonyltungsten)aluminylene | [N]–Al(THF)→W(CO)₅ | ⚠️ 大分子，Al 配位 THF |
| **4** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(4-dimethylaminopyridine)(pentacarbonyltungsten)aluminylene | [N]–Al(DMAP)→W(CO)₅ | ⚠️ 大分子，Al 配位 DMAP |

**备注**：化合物 2–4 展示了铝烯作为 σ-供体/σ-受体/π-受体的多模式配位能力。[pdf_1]

---

### 9. 化合物 5–7（路易斯酸加合物）

| 编号 | 英文名 | 缩写 | 备注 |
|:---:|:------|:----|:-----|
| **5** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(tris(pentafluorophenyl)borane)aluminylene | [N]–Al→B(C₆F₅)₃ | Al→B 配位键，附加 F→Al 相互作用 |
| **6** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(bis(pentafluorophenyl)borane)aluminylene | [N]–Al→HB(C₆F₅)₂ | Al→B 配位键 |
| **7** | (3,6-Di-tert-butyl-1,8-bis(3,5-di-tert-butylphenyl)carbazol-9-yl)(bis(pentafluorophenyl)borane)(diethyl ether)aluminylene | [N]–Al(HB(C₆F₅)₂)(OEt₂) | Et₂O 加合物，无 F→Al 相互作用 |

**文献**：张欣 & 刘柳, *Angew. Chem.* 2021 [pdf_1]

---

## 三、含末端 Al–Te 多重键的阴离子

### 10. K[Al(NON^Dipp)(Te)(THF)]

| 项目 | 内容 |
|:----|:------|
| **英文名** | Potassium [2,7-di-tert-butyl-9,9-dimethyl-4,5-bis(2,6-diisopropylphenylimino)xanthene-κ⁴O,N,N',O'](tellurido)(tetrahydrofuran)aluminate(III) |
| **英文缩写** | K[Al(NON^Dipp)(Te)(THF)] 或 K[1-Te-THF] |
| **SMILES** | ⚠️ 分子含 NON^Dipp 大环配体（4,5-双(2,6-二异丙基苯胺基)-2,7-二叔丁基-9,9-二甲基呫吨），结构过于复杂，无法用单行 SMILES 准确表达。 |
| **配体 NON^Dipp** | [O(SiMe₂NDipp)]₂²⁻，Dipp = 2,6-ⁱPr₂C₆H₃ |
| **Al–Te 键长** | 2.5039(7) Å |
| **WBI (Al–Te)** | 1.09 |
| **产率** | 32.4% |
| **备注** | 首个具有末端 Al–Te 多重键特征的阴离子铝碲化物。固态通过 Te···K 和 K···π 相互作用形成一维聚合物。 |

**文献**：Evans, Coles 等, *Chem. Commun.* 2021 [pdf_4]

---

### 11. [K(Et₂O)₃][Al(NON^Dipp)(Te{C(O)O}₂)]

| 项目 | 内容 |
|:----|:------|
| **英文名** | Potassium tris(diethyl ether) [2,7-di-tert-butyl-9,9-dimethyl-4,5-bis(2,6-diisopropylphenylimino)xanthene-κ⁴O,N,N',O'](tellurodicarbonate)aluminate(III) |
| **英文缩写** | CO₂ 双插入产物 |
| **SMILES** | ⚠️ 大分子，含 [{OC(O)}₂Te]²⁻ 碲代二碳酸酯配体，无法用单行 SMILES 准确表达。 |
| **备注** | CO₂ 通过 [2+2] 环加成分步双插入 Al–Te 键，生成前所未有的碲代二碳酸酯配体。¹²⁵Te NMR：δ –17.8 (d, J = 327 Hz)。 |

**文献**：Evans, Coles 等, *Chem. Commun.* 2021 (ESI) [pdf_6]

---

## 四、[Al₂Pd₂]²⁻ 簇系列

### 12. [Al₂Pd₂]²⁻ 簇（化合物 2）

| 项目 | 内容 |
|:----|:------|
| **英文名** | Dikalium bis(η³-aluminyl)bis(η²-cyclooctadiene)dipalladate(2–) cluster |
| **英文缩写** | [Al₂Pd₂]²⁻ cluster |
| **SMILES** | ⚠️ 团簇化合物，含 Al–Pd 多重键和全金属芳香性，无法用 SMILES 表示。 |
| **产率** | 83%（紫色固体，KC₈ 还原） |
| **备注** | 通过全金属双芳香性（σ + π）稳定形式三重态铝阴离子。 |

### 13–19. 化合物 3–9（[Al₂Pd₂]²⁻ 衍生物）

| 编号 | 配体/修饰 | 产率 | 外观 |
|:---:|:---------|:----:|:----:|
| **3** | Li 还原 | 88% | 品红色固体 |
| **4** | Na 还原 | 92% | 品红色固体 |
| **5** | 苯加合物 | 73% | 黄色晶体 |
| **6** | 甲苯加合物 | 75% | 黄色晶体 |
| **7** | 二苯乙炔加合物 | 71% | 深红色晶体 |
| **8** | 吡啶加合物（4 当量） | 73% | 黄色晶体 |
| **9** | Ni 掺杂（Cp₂Ni, 2 当量） | 52% | 绿色晶体 |

**文献**：Chen, Zhu 等, *JACS* (SI) [pdf_9]

---

## 五、简单有机底物（SMILES 可直接写出）

| 化合物 | 英文名 | SMILES |
|:------|:------|:-------|
| 苯乙炔 | Phenylacetylene | `C#Cc1ccccc1` |
| 苯甲腈 | Benzonitrile | `N#Cc1ccccc1` |
| 金刚烷叠氮 | 1-Adamantyl azide | `[N-]=[N+]=NC12CC3CC(C1)CC(C3)C2` |
| 碘甲烷 | Iodomethane | `CI` |
| 苯硅烷 | Phenylsilane | `c1ccccc1[SiH3]` |
| 六氟苯 | Hexafluorobenzene | `c1(c(c(c(c(c1F)F)F)F)F)F` |
| 二茂镍 | Bis(η⁵-cyclopentadienyl)nickel(II) | `[Ni]12345678([CH]9%10[CH]1%11[CH]2%12[CH]3%13[CH]49)[CH]5%10[CH]6%11[CH]7%12[CH]8%13[CH]59` ⚠️ |
| 双(三甲基硅基)乙炔 | Bis(trimethylsilyl)acetylene | `C[Si](C)(C)C#C[Si](C)(C)C` |

---

## 六、仅以类别提及的化合物（无具体分子式）

以下化合物类型由铝烯 **1** 合成，但文献中未给出具体分子式，无法写出 SMILES 和完整英文名：

| 化合物类别 | 英文名 | 说明 |
|:----------|:------|:-----|
| 铝硼烷 | Alumaboranes | B–Al 键化合物 |
| 铝硫属化物 | Aluminum chalcogenides | Al–S/Se/Te 化合物 |
| 铝二硫杂环丙烷 | Aluminadichalcogeniranes | 三元环含 Al + 2 个硫族原子 |
| 铝三硫杂环丁烷 | Aluminatrichalcogenetanes | 四元环含 Al + 3 个硫族原子 |
| 铝环丙烯 | Aluminacyclopropenes | 三元环含 Al + C=C |
| 铝降冰片二烯 | Aluminanorbornadienes | 双环[2.2.1]庚二烯含 Al |
| 铝环戊二烯 | Aluminacyclopentadienes | 五元环含 Al |
| 铝七碳三烯 | Aluminaheptatrienes | 七元环含 Al + 三个双键 |

---

*本文档基于知识库文献提供的信息整理。对于标注 ⚠️ 的化合物，其 SMILES 因分子过大、配位模式复杂或团簇结构而无法用标准 SMILES 准确表达，建议参考原文中的晶体结构图（ORTEP 图）获取完整结构信息。*