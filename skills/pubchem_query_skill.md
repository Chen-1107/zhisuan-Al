# 技能：PubChem 化合物查询

## 描述
通过 PubChem API 查询化合物的物理化学性质，使用 ConnectivitySMILES 获取正确 SMILES。

## 触发指令
- "查询 [化合物名称] 的性质"
- "获取 [SMILES] 的PubChem数据"
- "搜索 [化合物名称] 的分子量"

## 使用示例
用户：查询阿司匹林
助手：返回分子式、分子量、ConnectivitySMILES、LogP等

用户：查询 CC(=O)Oc1ccccc1C(=O)O
助手：根据SMILES检索，返回相应属性