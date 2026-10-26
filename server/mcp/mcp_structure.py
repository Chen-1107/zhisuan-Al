#!/usr/bin/env python3
"""
结构生成模块 - 图像识别、SMILES转3D、结构优化、PubChem查询
"""

from .mcp_base import MCPModule
from typing import Dict, Any, List
import os
import tempfile
import requests

# 导入现有模块
try:
    from mol_optimizer import smiles_to_molecule_graph, run_optimization
    MOL_AVAILABLE = True
except ImportError:
    MOL_AVAILABLE = False

class StructureModule(MCPModule):
    def __init__(self):
        super().__init__("Structure", "1.0.0")
        self._nodes = {
            "image_to_smiles": {
                "name": "图像识别为SMILES",
                "description": "使用DECIMER从分子结构图提取SMILES",
                "category": "结构生成",
                "inputs": [{"id": "image_path", "type": "string", "label": "图片路径"}],
                "outputs": [{"id": "smiles", "type": "string", "label": "SMILES"}],
                "config_schema": {}
            },
            "smiles_to_3d": {
                "name": "SMILES转3D结构",
                "description": "使用RDKit生成3D坐标",
                "category": "结构生成",
                "inputs": [{"id": "smiles", "type": "string", "label": "SMILES"}],
                "outputs": [
                    {"id": "mol_file", "type": "string", "label": "结构文件路径"},
                    {"id": "coord", "type": "object", "label": "3D坐标"}
                ],
                "config_schema": {
                    "add_h": {"type": "boolean", "default": True, "label": "添加氢原子"},
                    "force_field": {"type": "string", "default": "MMFF", "label": "力场"}
                }
            },
            "optimize_structure": {
                "name": "结构优化（MDP）",
                "description": "使用MDP强化学习优化分子结构",
                "category": "结构生成",
                "inputs": [
                    {"id": "smiles", "type": "string", "label": "SMILES"},
                    {"id": "mol_file", "type": "string", "label": "结构文件"}
                ],
                "outputs": [
                    {"id": "optimized_file", "type": "string", "label": "优化后结构"},
                    {"id": "energy", "type": "number", "label": "优化能量"}
                ],
                "config_schema": {
                    "episodes": {"type": "integer", "default": 30, "label": "训练轮数"},
                    "max_steps": {"type": "integer", "default": 60, "label": "每轮步数"}
                }
            },
            "load_from_pubchem": {
                "name": "从PubChem加载",
                "description": "从PubChem获取化合物信息",
                "category": "结构生成",
                "inputs": [{"id": "identifier", "type": "string", "label": "名称/CID/SMILES"}],
                "outputs": [
                    {"id": "smiles", "type": "string", "label": "SMILES"},
                    {"id": "properties", "type": "object", "label": "化合物性质"},
                    {"id": "cid", "type": "integer", "label": "CID"}
                ],
                "config_schema": {
                    "id_type": {"type": "string", "default": "name", "label": "标识符类型"}
                }
            },
            "check_novelty": {
                "name": "新颖性核验(PubChem)",
                "description": "用PubChem fastidentity同构检索判断候选SMILES是否已被收录;"
                               "未收录视为『数据库层面新颖』,作为DFT验证之外的查重步骤",
                "category": "结构生成/验证",
                "inputs": [{"id": "smiles", "type": "string", "label": "候选SMILES"}],
                "outputs": [
                    {"id": "cids", "type": "object", "label": "命中CID列表(空=未收录)"},
                    {"id": "novel", "type": "boolean", "label": "是否未收录(novel)"},
                    {"id": "source", "type": "string", "label": "依据(端点/状态码)"}
                ],
                "config_schema": {
                    "user_agent": {"type": "string", "label": "请求标识", "default": ""}
                }
            },
            "check_novelty_cross": {
                "name": "新颖性交叉核验(多库)",
                "description": "跨多个公共数据库(PubChem/ChEMBL/ChemSpider)交叉判断候选SMILES"
                               "是否已被收录;任一库命中→已收录,各库均未收录→数据库层面novel",
                "category": "结构生成/验证",
                "inputs": [{"id": "smiles", "type": "string", "label": "候选SMILES"}],
                "outputs": [
                    {"id": "normalized_smiles", "type": "string", "label": "规范化SMILES"},
                    {"id": "inchikey", "type": "string", "label": "InChIKey"},
                    {"id": "results", "type": "object", "label": "各库结果"},
                    {"id": "any_hit", "type": "object", "label": "是否任一库命中"},
                    {"id": "summary", "type": "string", "label": "交叉判定摘要"}
                ],
                "config_schema": {
                    "user_agent": {"type": "string", "label": "请求标识", "default": ""},
                    "chemspider_key": {"type": "string", "label": "ChemSpider API Key(可选)", "default": ""}
                }
            },
            "search_patents": {
                "name": "专利权威层检索(Espacenet OPS)",
                "description": "在Espacenet OPS(EPO免费注册)检索化合物被专利文本提及的情况,"
                               "作为登记库之外的『权威专利层』验证。注意: ①需免费注册的OPS凭据"
                               "(key+secret); ②基于文本(别名/CAS号)而非结构精确匹配,"
                               "对无CAS的全新分子检索力有限; ③REAXYS/SciFinder为商业付费库,"
                               "本模块无法自动化接入",
                "category": "结构生成/验证",
                "inputs": [{"id": "query", "type": "string", "label": "检索词(别名/CAS号/类名)"}],
                "outputs": [
                    {"id": "reachable", "type": "boolean", "label": "检索是否成功执行"},
                    {"id": "status", "type": "string", "label": "状态"},
                    {"id": "total_count", "type": "integer", "label": "命中专利数"},
                    {"id": "examples", "type": "object", "label": "示例专利号"},
                    {"id": "note", "type": "string", "label": "说明"}
                ],
                "config_schema": {
                    "espacenet_key": {"type": "string", "label": "Espacenet OPS Consumer Key", "default": ""},
                    "espacenet_secret": {"type": "string", "label": "Espacenet OPS Consumer Secret", "default": ""}
                }
            },
            "search_literature": {
                "name": "文献报道检索(PubMed/免key)",
                "description": "用NCBI eUtils(免key免登录, 需遵守限速)按化合物名/类名检索学术文献,"
                               "判断该物种是否已在文献中报道过, 作为登记库之外的『文献层』交叉验证。"
                               "注意: 按文本检索, 无命中不能直接等同于文献未报道",
                "category": "结构生成/验证",
                "inputs": [{"id": "term", "type": "string", "label": "检索词(化合物名/类名/CAS)"}],
                "outputs": [
                    {"id": "reachable", "type": "boolean", "label": "检索是否成功执行"},
                    {"id": "status", "type": "string", "label": "状态"},
                    {"id": "hit_count", "type": "integer", "label": "命中文献数"},
                    {"id": "pmids", "type": "object", "label": "示例PMID"},
                    {"id": "note", "type": "string", "label": "说明"}
                ],
                "config_schema": {}
            },
            "search_matrix": {
                "name": "多重检索词验证矩阵",
                "description": "对多个检索词逐一跨多个可编程源(PubChem按名/ChEMBL全文/PubMed文献/"
                               "可选Espacenet专利)打格子, 输出 term×source 命中矩阵并聚合: "
                               "全部可达格 0 命中→『文本/登记层面未见报道』, 并把覆盖范围与跳过格如实标注。"
                               "规避单一检索词的文本检索盲区, 是更全面的验证方式",
                "category": "结构生成/验证",
                "inputs": [
                    {"id": "terms", "type": "array", "label": "检索词列表(JSON 数组)"},
                    {"id": "smiles", "type": "string", "label": "候选SMILES(可选,用于推导缺省检索词)"}
                ],
                "outputs": [
                    {"id": "matrix", "type": "object", "label": "term×source 命中矩阵"},
                    {"id": "any_hit", "type": "boolean", "label": "是否存在命中格"},
                    {"id": "covered_terms", "type": "object", "label": "参与评估的检索词"},
                    {"id": "skipped_cells", "type": "object", "label": "跳过/不可达格"},
                    {"id": "summary", "type": "string", "label": "矩阵聚合判定"}
                ],
                "config_schema": {
                    "espacenet_key": {"type": "string", "label": "Espacenet OPS Consumer Key", "default": ""},
                    "espacenet_secret": {"type": "string", "label": "Espacenet OPS Consumer Secret", "default": ""}
                }
            }
        }

    def get_node_definitions(self) -> List[Dict[str, Any]]:
        return list(self._nodes.values())

    async def execute_node(self, node_id: str, inputs: Dict[str, Any],
                           config: Dict[str, Any]) -> Dict[str, Any]:
        if node_id == "image_to_smiles":
            return await self._image_to_smiles(inputs.get("image_path", ""))
        elif node_id == "smiles_to_3d":
            return await self._smiles_to_3d(inputs.get("smiles", ""), config)
        elif node_id == "optimize_structure":
            return await self._optimize_structure(inputs, config)
        elif node_id == "load_from_pubchem":
            return await self._load_from_pubchem(inputs.get("identifier", ""), config)
        elif node_id == "check_novelty":
            return await self._check_novelty(inputs.get("smiles", ""), config)
        elif node_id == "check_novelty_cross":
            return await self._check_novelty_cross(inputs.get("smiles", ""), config)
        elif node_id == "search_patents":
            return await self._search_patents(inputs.get("query", ""), config)
        elif node_id == "search_literature":
            return await self._search_literature(inputs.get("term", ""), config)
        elif node_id == "search_matrix":
            return await self._search_matrix(inputs.get("terms"), inputs.get("smiles", ""), config)
        raise ValueError(f"未知节点: {node_id}")

    async def _image_to_smiles(self, image_path: str) -> Dict:
        try:
            from decimer import Decimer
            decimer = Decimer()
            result = decimer.predict(image_path)
            return {"smiles": result.get("smiles", "")}
        except Exception as e:
            return {"smiles": "", "error": str(e)}

    async def _smiles_to_3d(self, smiles: str, config: Dict) -> Dict:
        if not MOL_AVAILABLE:
            return {"mol_file": "", "coord": [], "error": "mol_optimizer不可用"}
        try:
            mol = smiles_to_molecule_graph(smiles, add_hs=config.get("add_h", True))
            fd, path = tempfile.mkstemp(suffix=".mol")
            os.close(fd)
            # 简单导出：只保存坐标（实际应导出为MOL文件，这里仅演示）
            pos = mol.get_positions()
            return {"mol_file": path, "coord": pos.tolist()}
        except Exception as e:
            return {"mol_file": "", "coord": [], "error": str(e)}

    async def _optimize_structure(self, inputs: Dict, config: Dict) -> Dict:
        if not MOL_AVAILABLE:
            return {"optimized_file": "", "energy": 0, "error": "mol_optimizer不可用"}
        smiles = inputs.get("smiles")
        if not smiles:
            return {"optimized_file": "", "energy": 0, "error": "需要SMILES"}
        try:
            result = run_optimization(
                smiles,
                episodes=config.get("episodes", 30),
                max_steps=config.get("max_steps", 60)
            )
            return {
                "optimized_file": result.get("html_path", ""),
                "energy": result.get("total_energy", 0)
            }
        except Exception as e:
            return {"optimized_file": "", "energy": 0, "error": str(e)}

    async def _load_from_pubchem(self, identifier: str, config: Dict) -> Dict:
        id_type = config.get("id_type", "name")
        url = f"https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/{id_type}/{identifier}/property/ConnectivitySMILES,MolecularFormula,MolecularWeight,XLogP,CID/JSON"
        try:
            resp = requests.get(url, timeout=10)
            if resp.status_code == 200:
                data = resp.json()
                props = data["PropertyTable"]["Properties"][0]
                return {
                    "smiles": props.get("ConnectivitySMILES", ""),
                    "properties": props,
                    "cid": props.get("CID", 0)
                }
        except:
            pass
        return {"smiles": "", "properties": {}, "cid": 0}

    async def _check_novelty(self, smiles, config=None) -> Dict:
        """新颖性核验：用 PubChem fastidentity(同构连接性快速检索)判断候选是否已被收录。
        复用本模块一致的 PUG-REST + requests 调用风格; 空 CID 即『数据库层面未收录(novel)』。
        注意: 仅表示 PubChem 未见相同连接性, 不等同于全球专利/文献新颖性; 亦可能被 NCBI 限流(503)。"""
        config = config or {}
        if not smiles:
            return {"cids": [], "novel": False, "source": "缺少SMILES"}
        endpoint = ("https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/"
                    "fastidentity/smiles/{}/cids/JSON".format(smiles))
        headers = {
            "User-Agent": config.get("user_agent")
                          or "zs-aluminylene/1.0 (novelty checker)",
            "Accept": "application/json",
        }
        try:
            resp = requests.get(endpoint, timeout=config.get("timeout", 15),
                                headers=headers)
            if resp.status_code == 200:
                cids = resp.json().get("IdentifierList", {}).get("CID", [])
                return {"cids": cids, "novel": len(cids) == 0,
                        "source": f"fastidentity HTTP 200, 命中 {len(cids)} 个 CID"}
            if resp.status_code == 404:
                return {"cids": [], "novel": True,
                        "source": "fastidentity HTTP 404 未收录(novel)"}
            return {"cids": [], "novel": None,
                    "source": f"HTTP {resp.status_code} 暂无法判定(可能被限流/拦截)"}
        except Exception as e:
            return {"cids": [], "novel": None,
                    "source": f"ERR {type(e).__name__}: {e}"}

    @staticmethod
    def _canonicalize(smiles):
        """RDKit 规范化 SMILES 并派生 InChIKey(用于 ChEMBL 精确命中); 不可用则原样返回。"""
        try:
            from rdkit import Chem
            m = Chem.MolFromSmiles(smiles)
            if m is None:
                return smiles, None
            canon = Chem.MolToSmiles(m)
            try:
                key = Chem.InchiToInchiKey(Chem.MolToInchi(m))
            except Exception:
                key = None
            return canon, key
        except Exception:
            return smiles, None

    async def _check_novelty_cross(self, smiles, config=None) -> Dict:
        """多库交叉核验: PubChem(免key) + ChEMBL(免key) + ChemSpider(需key, 无则跳过)。
        任一库命中→已收录; 所有可达库均未收录→『数据库层面novel』。"""
        config = config or {}
        if not smiles:
            return {"normalized_smiles": "", "inchikey": None, "results": [],
                    "any_hit": None, "summary": "缺少SMILES"}
        timeout = config.get("timeout", 15)
        ua = config.get("user_agent") or "zs-aluminylene/1.0 (novelty checker)"
        canon, inchi = self._canonicalize(smiles)
        results = []

        # 1) PubChem fastidentity(同构)
        pub = await self._check_novelty(canon or smiles, {"user_agent": ua, "timeout": timeout})
        results.append({"db": "PubChem", "method": "fastidentity同构",
                        "reachable": pub["novel"] is not None,
                        "hit": (pub["novel"] is False) if pub["novel"] is not None else None,
                        "hit_count": len(pub.get("cids") or []),
                        "note": pub["source"]})

        # 2) ChEMBL(药物相关, 免key)
        #    · 有 InChIKey → 直接最精确命中 /molecule/{inchikey}
        #    · 无 InChIKey(铝宾等无机配位物) → 用 SMILES 检索后用 RDKit 规范比对,
        #      只统计规范 SMILES 与查询完全一致的记录, 避免子串/模糊检索的满页假命中
        try:
            from urllib.parse import quote
            if inchi:
                url = f"https://www.ebi.ac.uk/chembl/api/data/molecule/{inchi}.json"
                method = "InChIKey精确"
                r = requests.get(url, headers={"User-Agent": ua, "Accept": "application/json"},
                                 timeout=timeout)
                if r.status_code == 200:
                    n = 1 if "molecule_chembl_id" in r.json() else 0
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": n > 0, "hit_count": n, "note": f"HTTP 200, 精确命中 {n}"})
                elif r.status_code == 404:
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": False, "hit_count": 0, "note": "HTTP 404 未见"})
                else:
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": None, "hit_count": 0, "note": f"HTTP {r.status_code}"})
            else:
                url = ("https://www.ebi.ac.uk/chembl/api/data/molecule/"
                       f"search?q={quote(canon or smiles)}")
                method = "SMILES(规范一致比对)"
                r = requests.get(url, headers={"User-Agent": ua, "Accept": "application/json"},
                                 timeout=timeout)
                if r.status_code == 200:
                    exact = 0
                    for m in (r.json().get("molecules") or []):
                        ms = m.get("molecule_structures") or {}
                        smi = ms.get("canonical_smiles") or m.get("molecule_smiles")
                        if not smi:
                            continue
                        c, _ = self._canonicalize(smi)
                        if c and c == canon:
                            exact += 1
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": exact > 0, "hit_count": exact,
                                    "note": f"HTTP 200, 规范一致 {exact}"})
                elif r.status_code == 404:
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": False, "hit_count": 0, "note": "HTTP 404 未见"})
                else:
                    results.append({"db": "ChEMBL", "method": method, "reachable": True,
                                    "hit": None, "hit_count": 0, "note": f"HTTP {r.status_code}"})
        except Exception as e:
            results.append({"db": "ChEMBL", "method": "—", "reachable": False,
                            "hit": None, "hit_count": 0, "note": f"ERR {type(e).__name__}"})

        # 3) ChemSpider(官方 REST 需免费 API key, 实测无 key 一律 403; 无 key 自动跳过)
        key = (config.get("chemspider_key") or "").strip()
        if not key:
            results.append({"db": "ChemSpider", "method": "SMILES精确", "reachable": False,
                            "hit": None, "hit_count": 0, "note": "跳过(未配置API key)"})
        else:
            try:
                h = {"apikey": key, "Content-Type": "application/json", "Accept": "application/json"}
                r1 = requests.post("https://api.rsc.org/compounds/v1/filter/smiles",
                                   json={"smiles": canon or smiles, "matchType": "exact",
                                         "orderBy": "CSID", "orderDirection": "ascending"},
                                   headers=h, timeout=timeout)
                if r1.status_code == 200:
                    fid = r1.json().get("filterId")
                    r2 = requests.get(f"https://api.rsc.org/compounds/v1/filter/{fid}/results?start=0&count=5",
                                      headers={"apikey": key, "Accept": "application/json"}, timeout=timeout)
                    if r2.status_code == 200:
                        ids = (r2.json().get("results") or [])
                        results.append({"db": "ChemSpider", "method": "SMILES精确", "reachable": True,
                                        "hit": len(ids) > 0, "hit_count": len(ids),
                                        "note": f"HTTP 200, 命中 {len(ids)}"})
                    else:
                        results.append({"db": "ChemSpider", "method": "SMILES精确", "reachable": True,
                                        "hit": None, "hit_count": 0, "note": f"results HTTP {r2.status_code}"})
                else:
                    results.append({"db": "ChemSpider", "method": "SMILES精确", "reachable": True,
                                    "hit": None, "hit_count": 0, "note": f"filter HTTP {r1.status_code}"})
            except Exception as e:
                results.append({"db": "ChemSpider", "method": "SMILES精确", "reachable": False,
                                "hit": None, "hit_count": 0, "note": f"ERR {type(e).__name__}"})

        # 4) CACTUS(NCI) —— 免 key 的『外部结构可解析性』核验(不是登记库, 不判存在)
        #    · 服务器端能把你给的 SMILES 解析成标准 InChIKey → 结构是真实可解析的第三方背书
        #    · probe=True 标记: 仅作有效性背书, 不参与『已收录/未收录』聚合
        try:
            url = ("https://cactus.nci.nih.gov/chemical/structure/"
                   f"{canon or smiles}/stdinchikey")
            r = requests.get(url, headers={"User-Agent": ua}, timeout=timeout)
            if r.status_code == 200 and r.text.strip().startswith("InChIKey="):
                key_ext = r.text.strip().split("=", 1)[1]
                results.append({"db": "CACTUS(NCI)", "method": "stdinchikey外部解析",
                                "probe": True, "reachable": True, "hit": None,
                                "hit_count": 1, "note": f"外部可解析, InChIKey={key_ext[:27]}…"})
            else:
                results.append({"db": "CACTUS(NCI)", "method": "stdinchikey外部解析",
                                "probe": True, "reachable": r.status_code != 0,
                                "hit": None, "hit_count": 0, "note": f"HTTP {r.status_code} 未解析"})
        except Exception as e:
            results.append({"db": "CACTUS(NCI)", "method": "stdinchikey外部解析",
                            "probe": True, "reachable": False, "hit": None,
                            "hit_count": 0, "note": f"ERR {type(e).__name__}"})

        # 5) Wikidata(SPARQL 公开端点, 免key免登录) —— 按 InChIKey 查化合物实体
        #    存在实体=该结构已被命名/收录过, 属存在性探测
        if inchi:
            wq = (f'SELECT ?c WHERE {{ ?c wdt:P235 "{inchi}". }}')
            try:
                wr = requests.get("https://query.wikidata.org/sparql",
                                  params={"query": wq, "format": "json"},
                                  headers={"User-Agent": ua + " (mailto:dev@example.com)",
                                           "Accept": "application/sparql-results+json"},
                                  timeout=timeout)
                if wr.status_code == 200:
                    wqid = (wr.json().get("results", {}).get("bindings") or [])
                    results.append({"db": "Wikidata", "method": "InChIKey(P235)",
                                    "reachable": True, "hit": len(wqid) > 0,
                                    "hit_count": len(wqid),
                                    "note": f"HTTP 200, 实体 {len(wqid)} 个"})
                else:
                    results.append({"db": "Wikidata", "method": "InChIKey(P235)",
                                    "reachable": True, "hit": None, "hit_count": 0,
                                    "note": f"HTTP {wr.status_code}"})
            except Exception as e:
                results.append({"db": "Wikidata", "method": "InChIKey(P235)",
                                "reachable": False, "hit": None, "hit_count": 0,
                                "note": f"ERR {type(e).__name__}"})
        else:
            results.append({"db": "Wikidata", "method": "InChIKey(P235)",
                            "reachable": False, "hit": None, "hit_count": 0,
                            "note": "跳过(无InChIKey)"})

        # 聚合(仅对存在性探测项; CACTUS 等 probe 项仅作背书, 不参与判定)
        existence = [x for x in results if not x.get("probe")]
        hits = [x for x in existence if x["hit"] is True]
        reachable = [x for x in existence if x["reachable"]]
        any_hit = bool(hits)
        if hits:
            summary = ("已收录: 命中 " + ", ".join(f"{h['db']}({h['hit_count']})" for h in hits))
        elif reachable and all(x["hit"] is False for x in reachable):
            summary = "交叉确认: 全部可达库均未收录 → 数据库层面新颖(novel)"
        elif reachable and all(x["hit"] in (False, None) for x in reachable):
            summary = "未发现命中, 但部分库状态未知(503/跳过), 新颖性置信度打折"
        else:
            summary = "暂无法判定(全部库不可达/无结果)"
        return {"normalized_smiles": canon, "inchikey": inchi,
                "results": results, "any_hit": any_hit, "summary": summary}

    async def _search_patents(self, query, config=None) -> Dict:
        """专利权威层: Espacenet OPS(EPO 免费注册)按文本检索化合物被专利提及的情况。
        无凭据则跳过; REAXYS/SciFinder 为商业库无开放 API, 本模块不接入。
        按官方 OPS 3.2 two-legged OAuth 流程实现; 需真实凭据联调(本模块只保证跳过/降级路径已测)。"""
        config = config or {}
        key = (config.get("espacenet_key") or "").strip()
        secret = (config.get("espacenet_secret") or "").strip()
        timeout = config.get("timeout", 15)
        if not (key and secret):
            return {"reachable": False, "status": "skipped(未配置OPS凭据)",
                    "total_count": 0, "examples": [],
                    "note": "Espacenet OPS 需免费注册的 Consumer Key/Secret; REAXYS/SciFinder 为商业付费库, 无开放API, 无法自动化接入"}
        if not query.strip():
            return {"reachable": False, "status": "缺少检索词", "total_count": 0,
                    "examples": [], "note": "请提供化合物别名/CAS号等文本检索词"}
        try:
            import base64
            # 1) two-legged OAuth 取 token
            token_resp = requests.post(
                "https://ops.epo.org/3.2/auth/accesstoken",
                headers={"Authorization": "Basic " + base64.b64encode(
                    f"{key}:{secret}".encode()).decode()},
                data={"grant_type": "client_credentials"}, timeout=timeout)
            if token_resp.status_code != 200:
                return {"reachable": False, "status": f"token HTTP {token_resp.status_code}",
                        "total_count": 0, "examples": [],
                        "note": "OPS 凭据未获授权或网络受限, 请核对 key/secret"}
            token = token_resp.json().get("access_token")
        except Exception as e:
            return {"reachable": False, "status": f"ERR {type(e).__name__}",
                    "total_count": 0, "examples": [], "note": "OPS 鉴权失败"}

        try:
            cql = f'txt=("{query}")'
            resp = requests.get(
                "https://ops.epo.org/3.2/rest-services/published-data/search",
                headers={"Authorization": f"Bearer {token}",
                         "Accept": "application/json"},
                params={"q": cql, "Range": "1-10"}, timeout=timeout)
            if resp.status_code not in (200, 400):
                return {"reachable": True, "status": f"search HTTP {resp.status_code}",
                        "total_count": 0, "examples": [], "note": "检索被拒, 可能超配额"}
            qr = resp.json().get("query-result", {})
            total = int(qr.get("total-result-count", 0) or 0)
            # 宽容提取示例专利号, 避免 OPS 结构解析差异导致报错
            examples = []
            try:
                docs = (qr.get("search-result", {})
                        .get("publication-reference", [])
                        or qr.get("bibliographic-data", {}).get("publication-reference", []))
                if isinstance(docs, dict):
                    docs = [docs]
                for d in docs or []:
                    did = (d.get("publication-reference", d)
                           .get("document-id") or [])
                    if isinstance(did, dict):
                        did = [did]
                    num = next((x.get("text") for x in did
                                if x.get("@document-id-type") == "docnumber"), None)
                    if num:
                        examples.append(num)
            except Exception:
                pass
            return {"reachable": True, "status": f"OPS 200 命中 {total}",
                    "total_count": total, "examples": examples[:10],
                    "note": "按文本检索(别名/CAS); 未匹配专利不能直接等同『无专利』, 需配合 REAXYS/SciFinder 人工核验"}
        except Exception as e:
            return {"reachable": False, "status": f"ERR {type(e).__name__}",
                    "total_count": 0, "examples": [], "note": "OPS 检索网络异常"}

    async def _search_literature(self, term, config=None) -> Dict:
        """文献层免key检索: NCBI eUtils/PubMed。需遵守速率限制(不加API key时约3次/秒)。
        文本检索: 无命中不能直接等同『文献未报道』。"""
        config = config or {}
        if not (term or "").strip():
            return {"reachable": False, "status": "缺少检索词", "hit_count": 0,
                    "pmids": [], "note": "请提供化合物名/类名"}
        try:
            r = requests.get(
                "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi",
                params={"db": "pubmed", "term": term.strip(),
                        "retmode": "json", "retmax": 10},
                headers={"User-Agent": config.get("user_agent")
                         or "zs-aluminylene/1.0 (public literature checker)",
                         "Accept": "application/json"},
                timeout=config.get("timeout", 20))
            if r.status_code != 200:
                return {"reachable": True, "status": f"HTTP {r.status_code}",
                        "hit_count": 0, "pmids": [],
                        "note": "eUtils 检索被限流或拒绝, 请稍后/放慢节奏"}
            es = r.json().get("esearchresult", {})
            cnt = int(es.get("count", 0))
            return {"reachable": True, "status": f"PubMed 命中 {cnt}",
                    "hit_count": cnt, "pmids": es.get("idlist", [])[:10],
                    "note": "按文本检索, 无命中不等于文献未报道"}
        except Exception as e:
            return {"reachable": False, "status": f"ERR {type(e).__name__}",
                    "hit_count": 0, "pmids": [], "note": "eUtils 网络异常"}

    @staticmethod
    def _parse_terms(raw):
        """把输入的检索词(JSON数组/分隔字符串/单条)规范成非空列表。"""
        if raw is None:
            return []
        if isinstance(raw, list):
            return [str(t).strip() for t in raw if str(t).strip()]
        s = str(raw).strip()
        if not s:
            return []
        if s.startswith("["):
            try:
                import json
                val = json.loads(s)
                return [str(t).strip() for t in val if str(t).strip()]
            except Exception:
                pass
        for sep in (",", ";", "\n", "|"):
            if sep in s:
                return [t.strip() for t in s.split(sep) if t.strip()]
        return [s]

    async def _search_matrix(self, terms, smiles, config=None) -> Dict:
        """多重检索词验证矩阵: 对每个检索词跨 PubMed/PubChem按名/ChEMBL全文(可选Espacenet)
        各打一格, 输出命中矩阵。全部可达格=0 → 文本/登记层面未见报道。"""
        config = config or {}
        term_list = self._parse_terms(terms)
        if not term_list:
            # 缺省检索词: InChIKey 头块 + 本领域类名, 覆盖结构与类别两个角度
            _, inchi = self._canonicalize(smiles) if smiles else (None, None)
            base = ["aluminylene", "aluminylene selenium", "aluminylene tellurium"]
            if inchi:
                term_list = [inchi.split("-")[0], *base]
            else:
                term_list = base
        timeout = config.get("timeout", 20)
        ua = config.get("user_agent") or "zs-aluminylene/1.0 (matrix)"

        rows, skipped = [], []
        for term in term_list:
            from urllib.parse import quote
            tq = quote(term)

            # PubMed(文献) —— 复用 _search_literature
            lit = await self._search_literature(term, {"timeout": timeout, "user_agent": ua})
            rows.append({"term": term, "source": "PubMed(文献)", "status": lit["status"],
                         "hit_count": lit["hit_count"], "reachable": lit["reachable"]})

            # PubChem 按名(登记/别名)
            try:
                r = requests.get(f"https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/{tq}/cids/JSON",
                                 headers={"User-Agent": ua, "Accept": "application/json"}, timeout=timeout)
                if r.status_code == 200:
                    cids = r.json().get("IdentifierList", {}).get("CID", [])
                    rows.append({"term": term, "source": "PubChem(按名)", "status": f"HTTP 200 命中{len(cids)}",
                                 "hit_count": len(cids), "reachable": True})
                elif r.status_code == 404:
                    rows.append({"term": term, "source": "PubChem(按名)", "status": "HTTP 404 无此名",
                                 "hit_count": 0, "reachable": True})
                else:
                    rows.append({"term": term, "source": "PubChem(按名)", "status": f"HTTP {r.status_code}",
                                 "hit_count": 0, "reachable": True})
            except Exception as e:
                rows.append({"term": term, "source": "PubChem(按名)", "status": f"ERR {type(e).__name__}",
                             "hit_count": 0, "reachable": False})

            # ChEMBL 全文(名称/别名命中, 非精确)
            try:
                r = requests.get(f"https://www.ebi.ac.uk/chembl/api/data/molecule/search?q={tq}",
                                 headers={"User-Agent": ua, "Accept": "application/json"}, timeout=timeout)
                if r.status_code == 200:
                    n = len(r.json().get("molecules") or [])
                    rows.append({"term": term, "source": "ChEMBL(全文)", "status": f"HTTP 200 命中{n}",
                                 "hit_count": n, "reachable": True})
                else:
                    rows.append({"term": term, "source": "ChEMBL(全文)", "status": f"HTTP {r.status_code}",
                                 "hit_count": 0, "reachable": True})
            except Exception as e:
                rows.append({"term": term, "source": "ChEMBL(全文)", "status": f"ERR {type(e).__name__}",
                             "hit_count": 0, "reachable": False})

            # Espacenet 专利(可选凭据)
            ek, es = (config.get("espacenet_key") or "").strip(), (config.get("espacenet_secret") or "").strip()
            if ek and es:
                pat = await self._search_patents(term, {"espacenet_key": ek, "espacenet_secret": es})
                rows.append({"term": term, "source": "Espacenet(专利)", "status": pat["status"],
                             "hit_count": pat["total_count"], "reachable": pat["reachable"]})
            else:
                skipped.append({"term": term, "source": "Espacenet(专利)", "reason": "未配置OPS凭据"})
                rows.append({"term": term, "source": "Espacenet(专利)", "status": "跳过(无OPS凭据)",
                             "hit_count": 0, "reachable": False})

        evaluable = [x for x in rows if x["reachable"]]
        any_hit = any(x["hit_count"] > 0 for x in evaluable)
        hits = [x for x in evaluable if x["hit_count"] > 0]
        if hits:
            who = "; ".join(f"{h['term']}×{h['source']}={h['hit_count']}" for h in hits)
            summary = f"矩阵有命中: {who}"
        elif evaluable:
            n_src = len({x['source'] for x in evaluable})
            summary = (f"矩阵全零: {len(term_list)} 个检索词 × {n_src} 个源全为 0 命中"
                       f" → 文本/登记层面未见报道")
        else:
            summary = "矩阵无可评估格(全部源不可达), 无法判定"
        return {"matrix": rows, "any_hit": any_hit,
                "covered_terms": term_list, "skipped_cells": skipped, "summary": summary}