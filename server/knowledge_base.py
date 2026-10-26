#!/usr/bin/env python3
"""
知识库管理系统 - 加载、管理和调用 skills
独立模块，供主程序导入使用。
"""

import os
import re
import sys
import json
from typing import Dict, List, Optional, Any
from pathlib import Path
import threading
import requests

# ------------环境检测-------------
def _check_rdkit_env():
    if 'rdkit-env' not in sys.executable:
        print("⚠ 警告：当前未在 rdkit-env 环境中运行。")
        print("  建议5执行：conda activate rdkit-env")
        print("  否则某些功能（如RDKit解析）可能不可用。")
        
_check_rdkit_env()

class Skill:
    """单个技能对象"""
    def __init__(self, name: str, category: str, filepath: str, title: str = "",
                 description: str = "", content: str = "", tags: List[str] = None):
        self.name = name
        self.category = category
        self.filepath = filepath
        self.title = title
        self.description = description
        self.content = content
        self.tags = tags or []

    def to_dict(self) -> Dict:
        return {
            "name": self.name,
            "category": self.category,
            "title": self.title,
            "description": self.description,
            "tags": self.tags,
            "filepath": self.filepath
        }

    def __repr__(self):
        return f"Skill({self.name}, category={self.category})"


class KnowledgeBase:
    """知识库管理器 - 加载 skills 目录下的所有 .md 文件"""

    def __init__(self, skills_dir: str = None, auto_load: bool = True):
        """
        :param skills_dir: skills 根目录路径，默认为当前目录下的 skills 文件夹
        :param auto_load: 是否在初始化时自动加载
        """
        if skills_dir is None:
            # 自动查找：优先使用当前目录下的 skills
            base = os.path.dirname(os.path.abspath(__file__))
            skills_dir = os.path.join(base, "skills")
            if not os.path.exists(skills_dir):
                # 尝试上级目录
                skills_dir = os.path.join(os.path.dirname(base), "skills")
        self.skills_dir = skills_dir
        self._skills: Dict[str, Skill] = {}
        self._categories: Dict[str, List[Skill]] = {}
        self._loaded = False
        self._lock = threading.Lock()
        if auto_load:
            self.load()

    def load(self, force_reload: bool = False) -> bool:
        """加载所有技能文件，返回是否成功"""
        if self._loaded and not force_reload:
            return True
        with self._lock:
            if not os.path.exists(self.skills_dir):
                print(f"警告：skills 目录不存在: {self.skills_dir}")
                return False
            self._skills.clear()
            self._categories.clear()
            for root, dirs, files in os.walk(self.skills_dir):
                category = os.path.basename(root)
                if category == "skills":  # 根目录本身不当作分类
                    category = "root"
                for file in files:
                    if file.endswith(".md"):
                        filepath = os.path.join(root, file)
                        name = os.path.splitext(file)[0]
                        self._parse_skill(name, category, filepath)
            self._loaded = True
            print(f"知识库加载完成：共 {len(self._skills)} 个技能，{len(self._categories)} 个分类。")
            return True

    def _parse_skill(self, name: str, category: str, filepath: str):
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
        except Exception as e:
            print(f"读取文件失败 {filepath}: {e}")
            return

        title_match = re.search(r'^#\s+(.*?)$', content, re.MULTILINE)
        title = title_match.group(1).strip() if title_match else name

        desc_match = re.search(r'^#\s+.*?\n(.*?)(?=\n#|\n\n|$)', content, re.DOTALL | re.MULTILINE)
        description = desc_match.group(1).strip() if desc_match else ""

        tags = re.findall(r'#([^\s#]+)', content)

        skill = Skill(
            name=name,
            category=category,
            filepath=filepath,
            title=title,
            description=description,
            content=content,
            tags=tags
        )
        self._skills[name] = skill
        self._categories.setdefault(category, []).append(skill)

    def list_skills(self, category: str = None) -> List[Skill]:
        if not self._loaded:
            self.load()
        if category:
            return self._categories.get(category, [])
        return list(self._skills.values())

    def list_categories(self) -> List[str]:
        if not self._loaded:
            self.load()
        return list(self._categories.keys())

    def get_skill(self, name: str) -> Optional[Skill]:
        if not self._loaded:
            self.load()
        return self._skills.get(name)

    def search_skills(self, keyword: str) -> List[Skill]:
        if not self._loaded:
            self.load()
        results = []
        keyword_lower = keyword.lower()
        for skill in self._skills.values():
            if (keyword_lower in skill.title.lower() or
                keyword_lower in skill.description.lower() or
                keyword_lower in skill.content.lower() or
                any(keyword_lower in tag.lower() for tag in skill.tags)):
                results.append(skill)
        return results

    def get_skill_content(self, name: str) -> Optional[str]:
        skill = self.get_skill(name)
        return skill.content if skill else None

    def reload(self):
        self._loaded = False
        self.load()

    def get_stats(self) -> Dict:
        if not self._loaded:
            self.load()
        return {
            "total_skills": len(self._skills),
            "categories": list(self._categories.keys()),
            "skills_per_category": {cat: len(skills) for cat, skills in self._categories.items()}
        }

    def query_pubchem(self, identifier: str, id_type: str = "name", 
                      properties: List[str] = None) -> Optional[Dict[str, Any]]:
        
        if properties is None:
            properties = ["MolecularFormula", "MolecularWeight", "ConnectivitySMILES", 
                          "InChI", "InChIKey", "IUPACName", "XLogP", "TPSA",
                          "HBondDonorCount", "HBondAcceptorCount", "RotatableBondCount"
                          ]
            
            if "ConnectivitySMILES" not in properties:
                properties.append("ConnectivitySMILES")
                
            prop_str = ",".join(properties)
            url = f"https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/{id_type}/{identifier}/property/{prop_str}/JSON"
            
            try:
                response = requests.get(url, timeout=10)
                response.raise_for_status()
                data = response.json()
                if "PropertyTable" in data and "Properties" in data["PropertyTable"]:
                    result = data["PropertyTable"]["Properties"][0]
                    
                    if "CID" in result:
                        result["CID"] = int(result["CID"])
                    return result
                else:
                    print(f"PubChem 未找到化合物 : {identifier}")
                    return None
            except requests.exceptions.RequestException as e:
                print(f"PubChem 请求失败: {e}")
                return None
                


# 单例模式
_default_kb = None

def get_knowledge_base(skills_dir: str = None) -> KnowledgeBase:
    global _default_kb
    if _default_kb is None:
        _default_kb = KnowledgeBase(skills_dir)
        _default_kb.load()
    return _default_kb