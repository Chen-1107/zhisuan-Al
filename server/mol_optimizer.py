"""
化学直觉数学建模 + RDKit 集成 + Q-Learning 训练 + 球棍模型可视化（氢原子开关）
完整版，包含所有力场、约束、MDP、训练和可视化。
"""

import math
import random
import numpy as np
import webbrowser
import os
import json
import subprocess
import tempfile
import tkinter as tk
from tkinter import ttk
from typing import List, Tuple, Dict, Optional

from rdkit import Chem
from rdkit.Chem import AllChem

# C++计算核心（力场/约束/MDP/Q-Learning的加速实现，与Python数值等价）
MOLOPT_EXE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "molopt.exe")

# ============================================================
# 1. 分子图的数学表示
# ============================================================

class Atom:
    def __init__(self, symbol: str, Z: int, q: int = 0, m: int = 1,
                 lone_pairs: int = 0, coords: np.ndarray = None):
        self.symbol = symbol
        self.Z = Z
        self.q = q
        self.m = m
        self.lone_pairs = lone_pairs
        self.coords = np.array(coords, dtype=float) if coords is not None else np.zeros(3)
        self.neighbors: List[int] = []
        self.bonds: Dict[int, float] = {}
        self.hybridization = None
        self.n_H = 0

    def __repr__(self):
        return f"Atom({self.symbol}, Z={self.Z}, coords={self.coords})"


class MoleculeGraph:
    def __init__(self, atoms: List[Atom], bond_list: List[Tuple[int, int, float]]):
        self.atoms = atoms
        self.n = len(atoms)
        for i, j, b in bond_list:
            self.atoms[i].neighbors.append(j)
            self.atoms[i].bonds[j] = b
            self.atoms[j].neighbors.append(i)
            self.atoms[j].bonds[i] = b
        self.bond_list = bond_list
        self.theta0_map = {'sp': 180.0, 'sp2': 120.0, 'sp3': 109.5}
        self._compute_derived()

    # 过渡金属：配体由 SMILES 显式给出，不加隐式氢（如 Cu(I) 线性二配位）
    TRANSITION_METALS = {'Cu', 'Zn', 'Ni', 'Fe', 'Co', 'Cr', 'Mn', 'Pd', 'Pt',
                         'Ag', 'Au', 'Cd', 'Hg', 'Ti', 'V', 'Zr', 'W', 'Mo'}

    def _compute_derived(self):
        valency_map = {'C': 4, 'H': 1, 'O': 2, 'N': 3, 'F': 1, 'Cl': 1, 'Br': 1, 'I': 1,
                       'S': 2, 'P': 3, 'B': 3, 'Si': 4, 'Al': 3,
                       'Se': 2, 'Te': 2, 'Cu': 4}
        for i, atom in enumerate(self.atoms):
            total_bond_order = sum(atom.bonds.values())
            valency = valency_map.get(atom.symbol, 4)
            # |q|：形式电荷（无论正负）都消耗成键容量——[Se-] 1键0H（3孤对），
            # [Al+] 3键0H；旧公式 -q 对负离子会把孤对误算成隐式氢（幻影氢）
            if atom.symbol in MoleculeGraph.TRANSITION_METALS:
                atom.n_H = 0
            else:
                atom.n_H = max(0, valency - total_bond_order - abs(atom.q))
            steric = total_bond_order + atom.lone_pairs
            if steric == 2:
                atom.hybridization = 'sp'
            elif steric == 3:
                atom.hybridization = 'sp2'
            elif steric >= 4:
                atom.hybridization = 'sp3'
            else:
                atom.hybridization = 'sp3'

    def get_positions(self) -> np.ndarray:
        return np.array([atom.coords for atom in self.atoms])

    def set_positions(self, pos: np.ndarray):
        for i, atom in enumerate(self.atoms):
            atom.coords = pos[i].copy()

    def get_bond_length(self, i: int, j: int) -> float:
        return np.linalg.norm(self.atoms[i].coords - self.atoms[j].coords)

    def get_angle(self, i: int, j: int, k: int) -> float:
        v1 = self.atoms[i].coords - self.atoms[j].coords
        v2 = self.atoms[k].coords - self.atoms[j].coords
        norm1 = np.linalg.norm(v1)
        norm2 = np.linalg.norm(v2)
        if norm1 == 0 or norm2 == 0:
            return 0.0
        cos = np.clip(np.dot(v1, v2) / (norm1 * norm2), -1.0, 1.0)
        return math.acos(cos)

    def get_dihedral(self, i: int, j: int, k: int, l: int) -> float:
        v1 = self.atoms[i].coords - self.atoms[j].coords
        v2 = self.atoms[k].coords - self.atoms[j].coords
        v3 = self.atoms[l].coords - self.atoms[k].coords
        n1 = np.cross(v1, v2)
        n2 = np.cross(v2, v3)
        if np.linalg.norm(n1) == 0 or np.linalg.norm(n2) == 0:
            return 0.0
        n1 = n1 / np.linalg.norm(n1)
        n2 = n2 / np.linalg.norm(n2)
        v2_hat = v2 / np.linalg.norm(v2)
        angle = math.atan2(np.dot(np.cross(n1, v2_hat), n2), np.dot(n1, n2))
        return angle

    def get_angles(self) -> List[Tuple[int, int, int]]:
        angles = []
        for j, atom in enumerate(self.atoms):
            neigh = atom.neighbors
            if len(neigh) >= 2:
                for a in range(len(neigh)):
                    for b in range(a+1, len(neigh)):
                        angles.append((neigh[a], j, neigh[b]))
        return angles

    def get_dihedrals(self) -> List[Tuple[int, int, int, int]]:
        dihedrals = []
        for j, atom_j in enumerate(self.atoms):
            for k in atom_j.neighbors:
                if k <= j:
                    continue
                for i in atom_j.neighbors:
                    if i == k:
                        continue
                for l in self.atoms[k].neighbors:
                    if l == j:
                        continue
                    dihedrals.append((i, j, k, l))
        return dihedrals


# ============================================================
# 2. SMILES -> MoleculeGraph (RDKit)
# ============================================================

def _parse_relaxed(smiles: str):
    """放宽化合价检查解析（供Al=Al双键等奇异价态物种使用）。
    RDKit限制Al最大形式价3，铝宾二聚体等结构的Al形式价为4。"""
    mol = Chem.MolFromSmiles(smiles, sanitize=False)
    if mol is None:
        return None
    try:
        mol.UpdatePropertyCache(strict=False)
        Chem.SanitizeMol(mol, sanitizeOps=Chem.SanitizeFlags.SANITIZE_ALL ^
                         Chem.SanitizeFlags.SANITIZE_PROPERTIES)
        return mol
    except Exception:
        return None


def _make_embeddable(mol):
    """生成可被ETKDG嵌入的副本：把导致原子超价的多重键降级为单键。
    只改键级、不动原子顺序，故坐标可按原子索引回填到真实拓扑。
    若无超价原子则返回None（可直接嵌入原分子）。"""
    limits = {'C': 4, 'H': 1, 'O': 2, 'N': 3, 'F': 1, 'Cl': 1, 'Br': 1, 'I': 1,
              'S': 2, 'P': 3, 'B': 3, 'Si': 4, 'Al': 3}
    over = {a.GetIdx() for a in mol.GetAtoms()
            if sum(b.GetBondTypeAsDouble() for b in a.GetBonds()) > limits.get(a.GetSymbol(), 4) + 1e-9}
    if not over:
        return None
    emb = Chem.Mol(mol)
    for _ in range(10):
        over = {a.GetIdx() for a in emb.GetAtoms()
                if sum(b.GetBondTypeAsDouble() for b in a.GetBonds()) > limits.get(a.GetSymbol(), 4) + 1e-9}
        if not over:
            break
        changed = False
        for b in emb.GetBonds():
            if b.GetBondTypeAsDouble() <= 1.0:
                continue
            if b.GetBeginAtomIdx() in over or b.GetEndAtomIdx() in over:
                b.SetBondType(Chem.rdchem.BondType.SINGLE)
                b.SetIsAromatic(False)
                changed = True
        if not changed:
            break
    emb.UpdatePropertyCache(strict=True)
    Chem.SanitizeMol(emb)
    return emb


def smiles_to_molecule_graph(smiles: str, add_hs: bool = True,
                             embed_seed: int = None) -> MoleculeGraph:
    # embed_seed: ETKDG 嵌入随机种子（None=随机；固定后初始构象可复现，
    # C++ 端 SEED 只控制优化过程，控制不了 RDKit 初始嵌入）
    mol = Chem.MolFromSmiles(smiles)
    embed_mol = None  # 超价物种的可嵌入副本（键级已降级）
    if mol is None:
        # 奇异价态物种（如Al=Al双键）：放宽化合价检查解析真实拓扑
        mol = _parse_relaxed(smiles)
        if mol is None:
            raise ValueError(f"无法解析 SMILES: {smiles}")
        embed_mol = _make_embeddable(mol)
        if embed_mol is None:
            raise ValueError(f"无法生成3D坐标: {smiles}")
    if add_hs:
        mol = Chem.AddHs(mol)
        if embed_mol is not None:
            embed_mol = Chem.AddHs(embed_mol)
            if embed_mol.GetNumAtoms() != mol.GetNumAtoms():
                raise ValueError(f"键级降级副本与原分子原子数不一致: {smiles}")
    target = embed_mol if embed_mol is not None else mol
    params = AllChem.ETKDGv3()
    if embed_seed is not None:
        params.randomSeed = embed_seed
    cid = AllChem.EmbedMolecule(target, params)
    if cid < 0:
        # ETKDG失败（大环/金属中心常见），随机坐标重试
        params = AllChem.ETKDGv3()
        params.useRandomCoords = True
        if embed_seed is not None:
            params.randomSeed = embed_seed
        cid = AllChem.EmbedMolecule(target, params)
    if cid < 0:
        raise ValueError(f"无法生成3D坐标: {smiles}")
    try:
        AllChem.MMFFOptimizeMolecule(target, maxIters=200)
    except Exception:
        # MMFF94不含Al等金属参数，回退UFF（覆盖元素周期表绝大部分）
        try:
            AllChem.UFFOptimizeMolecule(target, maxIters=200)
        except Exception:
            pass  # 保留ETKDG原始坐标

    conf = target.GetConformer()
    atom_idx_map = {}
    atoms = []
    lone_pairs_map = {'N': 1, 'O': 2, 'F': 3, 'Cl': 3, 'Br': 3, 'I': 3, 'S': 2, 'P': 1, 'Se': 2, 'Te': 2}

    for idx, rd_atom in enumerate(mol.GetAtoms()):
        symbol = rd_atom.GetSymbol()
        Z = rd_atom.GetAtomicNum()
        q = rd_atom.GetFormalCharge()
        m = 1
        lone_pairs = lone_pairs_map.get(symbol, 0) if symbol != 'H' else 0
        pos = conf.GetAtomPosition(idx)
        coords = np.array([pos.x, pos.y, pos.z])
        atom = Atom(symbol, Z, q, m, lone_pairs, coords)
        atoms.append(atom)
        atom_idx_map[idx] = len(atoms) - 1

    bond_list = []
    for rd_bond in mol.GetBonds():
        i = rd_bond.GetBeginAtomIdx()
        j = rd_bond.GetEndAtomIdx()
        bt = rd_bond.GetBondType()
        if bt == Chem.rdchem.BondType.SINGLE:
            order = 1.0
        elif bt == Chem.rdchem.BondType.DOUBLE:
            order = 2.0
        elif bt == Chem.rdchem.BondType.TRIPLE:
            order = 3.0
        elif bt == Chem.rdchem.BondType.AROMATIC:
            order = 1.5
        else:
            order = 1.0
        bond_list.append((atom_idx_map[i], atom_idx_map[j], order))

    return MoleculeGraph(atoms, bond_list)


# ============================================================
# 3. CIF 实验参考键长（实验结构修正）
# ============================================================

# 通用力场参数可靠性低的元素：这些键的平衡长度优先采用实验（CIF）值
EXOTIC_ELEMENTS = ("Al", "B", "Si", "P", "Se", "Te", "Ga", "Ge", "As", "Sb",
                   "Li", "Na", "K", "Mg", "Ca", "Zn", "Cu", "Ni", "Fe", "Co",
                   "Cr", "Mn", "Pd", "Pt", "Ag", "Au", "Cd", "Hg", "Sn", "Pb",
                   "Ti", "V", "Zr", "W", "Mo")

# 共价半径（Å，Pyykkö 2009），用于实验结构中的成键判定
_COVR_ANG = {"H": 0.31, "C": 0.76, "N": 0.71, "O": 0.66, "F": 0.57,
             "Al": 1.21, "Se": 1.20, "Te": 1.38, "Cu": 1.32, "Cl": 1.02,
             "B": 0.84, "Si": 1.11, "P": 1.07, "S": 1.05, "Br": 1.20, "I": 1.39,
             "Ga": 1.22, "Ge": 1.20, "Sn": 1.39, "Pb": 1.46, "Zn": 1.22,
             "Ni": 1.24, "Fe": 1.32, "Co": 1.26, "Mn": 1.39, "Cr": 1.39,
             "Ag": 1.45, "Au": 1.36, "Pd": 1.39, "Pt": 1.36, "Mg": 1.41,
             "Li": 1.28, "Na": 1.66, "K": 2.03, "Ca": 1.76}


def normalize_ref_bonds(ref_bonds: Dict) -> Dict[Tuple[str, str], float]:
    """归一化参考键长表。键可为 ("Al","Se") 元组或 "Al-Se" 字符串，值为 Å。
    返回 {(elem_a, elem_b) 排序后: 长度Å}。"""
    out = {}
    for k, v in ref_bonds.items():
        if isinstance(k, str):
            parts = k.split("-")
            if len(parts) != 2 or not all(parts):
                raise ValueError(f"参考键长键格式错误（应为 'Al-Se' 或 ('Al','Se')）: {k!r}")
            key = (parts[0].strip(), parts[1].strip())
        else:
            key = tuple(k)
            if len(key) != 2:
                raise ValueError(f"参考键长键格式错误（应为 ('Al','Se') 二元组）: {k!r}")
        out[tuple(sorted(key))] = float(v)
    return out


def ref_bonds_from_xyz(xyz_path: str, elements=None) -> Dict[Tuple[str, str], float]:
    """从实验结构 XYZ（如 CIF 解析产物）提取参考键长表。

    只取至少一端元素在 elements（默认 EXOTIC_ELEMENTS）中的键，
    同元素对多处出现时取平均。返回 {("Al","Se"): 2.1935, ...}（Å）。"""
    if elements is None:
        elements = EXOTIC_ELEMENTS
    elements = set(elements)
    lines = open(xyz_path).read().strip().split("\n")
    atoms = []
    for line in lines[2:]:
        p = line.split()
        if len(p) >= 4:
            try:
                atoms.append((p[0], float(p[1]), float(p[2]), float(p[3])))
            except ValueError:
                continue
    sums = {}
    cnts = {}
    for i in range(len(atoms)):
        for j in range(i + 1, len(atoms)):
            si, sj = atoms[i][0], atoms[j][0]
            if si not in elements and sj not in elements:
                continue
            if si == "H" and sj == "H":
                continue
            d = float(np.linalg.norm(np.array(atoms[i][1:4]) - np.array(atoms[j][1:4])))
            lim = _COVR_ANG.get(si, 1.0) + _COVR_ANG.get(sj, 1.0) + 0.45
            if 0.4 < d < lim:
                key = tuple(sorted((si, sj)))
                sums[key] = sums.get(key, 0.0) + d
                cnts[key] = cnts.get(key, 0) + 1
    return {k: sums[k] / cnts[k] for k in sums}


def _expand_ref_map(mol: "MoleculeGraph", ref_bonds) -> Dict[Tuple[int, int], float]:
    """把元素对级参考键长（Å）展开为具体键的 {（原子序号排序对）: r0_pm}。"""
    if not ref_bonds:
        return {}
    norm = normalize_ref_bonds(ref_bonds)
    out = {}
    for i, j, b in mol.bond_list:
        si = mol.atoms[i].symbol
        sj = mol.atoms[j].symbol
        key = tuple(sorted((si, sj)))
        if key in norm:
            out[(i, j) if i < j else (j, i)] = norm[key] * 100.0  # Å -> pm
    return out


def normalize_error_bonds(error_bonds) -> Dict[Tuple[str, str], list]:
    """归一化错误集键长表。键为 ("Al","C") 元组或 "Al-C" 字符串，
    值为 [{r: 错误键长Å, w: 惩罚权重}, ...]（也可简化为单个数值=键长，w 取默认 10）。
    返回 {(elem_a, elem_b) 排序后: [(r_pm, w), ...]}。"""
    out = {}
    for k, v in error_bonds.items():
        if isinstance(k, str):
            parts = k.split("-")
            if len(parts) != 2 or not all(parts):
                raise ValueError(f"错误键长键格式错误（应为 'Al-C' 或 ('Al','C')）: {k!r}")
            key = tuple(sorted((parts[0].strip(), parts[1].strip())))
        else:
            key = tuple(sorted(k))
            if len(key) != 2:
                raise ValueError(f"错误键长键格式错误（应为 ('Al','C') 二元组）: {k!r}")
        terms = v if isinstance(v, list) else [v]
        norm_terms = []
        for t in terms:
            if isinstance(t, dict):
                r, w = float(t["r"]), float(t.get("w", 10.0))
            else:
                r, w = float(t), 10.0
            norm_terms.append((r * 100.0, w))  # Å -> pm
        out[key] = norm_terms
    return out


def _expand_error_map(mol: "MoleculeGraph", error_bonds) -> Dict[Tuple[int, int], list]:
    """把元素对级错误键长展开为具体键的 {（原子序号排序对）: [(r_err_pm, w), ...]}。"""
    if not error_bonds:
        return {}
    norm = normalize_error_bonds(error_bonds)
    out = {}
    for i, j, b in mol.bond_list:
        si = mol.atoms[i].symbol
        sj = mol.atoms[j].symbol
        key = tuple(sorted((si, sj)))
        if key in norm:
            out[(i, j) if i < j else (j, i)] = norm[key]
    return out


# ============================================================
# 4. 分子力场能量函数
# ============================================================

def _share_neighbor(atoms, i, j):
    """i,j 是否共享一个共同邻居（1,3-位原子对，由同一中心分隔）。
    化学力场惯例：1,2(成键)与1,3(键角约束已涵盖)都不属于vdW作用范围，
    只对1,4及以上的原子对施加范德华作用。"""
    ni = atoms[i].neighbors
    for v in ni:
        if v != j and v in atoms[j].neighbors:
            return True
    return False


class ForceField:
    def __init__(self, mol: MoleculeGraph, ref_bonds: Dict = None,
                 error_bonds: Dict = None):
        self.mol = mol
        self.k_b = 1.0
        self.k_theta = 0.1
        self.epsilon = 0.1
        self.vdw_radii = {'C': 170, 'H': 120, 'O': 152, 'N': 155, 'F': 147, 'Cl': 175, 'Br': 185, 'I': 198,
                          'S': 180, 'P': 180, 'B': 180, 'Si': 210, 'Al': 184,
                          'Se': 190, 'Te': 206, 'Cu': 140}
        self.cov_radii = {'C': 77, 'H': 31, 'O': 73, 'N': 71, 'F': 64, 'Cl': 99, 'Br': 114, 'I': 133,
                          'S': 104, 'P': 107, 'B': 87, 'Si': 111, 'Al': 121,
                          'Se': 120, 'Te': 138, 'Cu': 132}
        # CIF 实验参考键长：{（原子序号排序对）: r0_pm}，优先于共价半径求和
        self.ref_map = _expand_ref_map(mol, ref_bonds)
        # 错误集键长（二重态错误建模特征）：{（原子序号排序对）: [(r_err_pm, w), ...]}
        self.err_map = _expand_error_map(mol, error_bonds)

    def error_penalty(self) -> float:
        """错误集键长惩罚（与 C++ Forces::errPenalty 公式一致，σ=10pm）：
        E_err = Σ w·exp(-((r-r_err)/10pm)²) —— 键长逼近已知错误值时惩罚最大。"""
        if not self.err_map:
            return 0.0
        E = 0.0
        for (i, j), terms in self.err_map.items():
            r = self.mol.get_bond_length(i, j) * 100.0  # Å -> pm
            for r_err, w in terms:
                d = (r - r_err) / 10.0
                E += w * math.exp(-d * d)
        return E

    def bond_r0_pm(self, i: int, j: int, b: float) -> float:
        """键 i-j 的平衡长度（pm）：有实验参考值时用参考值，否则共价半径求和。"""
        key = (i, j) if i < j else (j, i)
        r0 = self.ref_map.get(key)
        if r0 is not None:
            return r0
        return (self.cov_radii.get(self.mol.atoms[i].symbol, 77) +
                self.cov_radii.get(self.mol.atoms[j].symbol, 77)) + 0.1 * (1 - b)

    def bond_energy(self) -> float:
        E = 0.0
        for i, j, b in self.mol.bond_list:
            r = self.mol.get_bond_length(i, j) * 100  # Å -> pm
            r0 = self.bond_r0_pm(i, j, b)
            E += 0.5 * self.k_b * (r - r0) ** 2
        return E

    def angle_energy(self) -> float:
        E = 0.0
        for i, j, k in self.mol.get_angles():
            theta = self.mol.get_angle(i, j, k)
            hyb = self.mol.atoms[j].hybridization
            theta0_deg = self.mol.theta0_map.get(hyb, 109.5)
            theta0 = math.radians(theta0_deg)
            E += 0.5 * self.k_theta * (theta - theta0) ** 2
        return E

    def torsion_energy(self) -> float:
        E = 0.0
        V3 = 1.0
        for i, j, k, l in self.mol.get_dihedrals():
            phi = self.mol.get_dihedral(i, j, k, l)
            E += V3 * (1 + math.cos(3 * phi))
        return E

    def vdw_energy(self) -> float:
        E = 0.0
        atoms = self.mol.atoms
        n = len(atoms)
        for i in range(n):
            for j in range(i+1, n):
                if j in atoms[i].neighbors:
                    continue
                if _share_neighbor(atoms, i, j):
                    continue
                r = np.linalg.norm(atoms[i].coords - atoms[j].coords)
                if r < 0.001:
                    E += 1e10
                    continue
                rmin = (self.vdw_radii.get(atoms[i].symbol, 170) +
                        self.vdw_radii.get(atoms[j].symbol, 170)) / 100
                E += 4 * self.epsilon * ((rmin / r) ** 12 - (rmin / r) ** 6)
        return E

    def electrostatic_energy(self) -> float:
        E = 0.0
        atoms = self.mol.atoms
        n = len(atoms)
        for i in range(n):
            for j in range(i+1, n):
                if j in atoms[i].neighbors:
                    continue
                r = np.linalg.norm(atoms[i].coords - atoms[j].coords)
                if r < 0.001:
                    E += 1e10
                    continue
                E += atoms[i].q * atoms[j].q / r
        return E

    def total_energy(self) -> float:
        return (self.bond_energy() + self.angle_energy() + self.torsion_energy() +
                self.vdw_energy() + self.electrostatic_energy())

    def gradient(self, delta=1e-5) -> np.ndarray:
        pos = self.mol.get_positions()
        grad = np.zeros_like(pos)
        n_atoms = len(self.mol.atoms)
        for i in range(n_atoms):
            for axis in range(3):
                pos_plus = pos.copy()
                pos_plus[i, axis] += delta
                self.mol.set_positions(pos_plus)
                E_plus = self.total_energy()
                pos_minus = pos.copy()
                pos_minus[i, axis] -= delta
                self.mol.set_positions(pos_minus)
                E_minus = self.total_energy()
                grad[i, axis] = (E_plus - E_minus) / (2 * delta)
        self.mol.set_positions(pos)
        return grad


# ============================================================
# 4. 约束引擎
# ============================================================

class ConstraintEngine:
    def __init__(self, mol: MoleculeGraph, ref_bonds: Dict = None):
        self.mol = mol
        self.bond_tolerance = 0.3
        self.angle_tolerance = 30.0
        self.vdw_factor = 0.7
        self.valency_tolerance = 1
        self.vdw_radii_ang = {'C': 1.70, 'H': 1.20, 'O': 1.52, 'N': 1.55,
                              'F': 1.47, 'Cl': 1.75, 'Br': 1.85, 'I': 1.98,
                              'S': 1.80, 'P': 1.80, 'B': 1.80, 'Si': 2.10, 'Al': 1.84,
                              'Se': 1.90, 'Te': 2.06, 'Cu': 1.40}
        self.valency_map = {'C': 4, 'H': 1, 'O': 2, 'N': 3, 'F': 1, 'Cl': 1, 'Br': 1, 'I': 1,
                            'S': 2, 'P': 3, 'B': 3, 'Si': 4, 'Al': 3,
                            'Se': 2, 'Te': 2, 'Cu': 4}
        self.cov_radii = {'C': 77, 'H': 31, 'O': 73, 'N': 71, 'F': 64, 'Cl': 99, 'Br': 114, 'I': 133,
                          'S': 104, 'P': 107, 'B': 87, 'Si': 111, 'Al': 121,
                          'Se': 120, 'Te': 138, 'Cu': 132}
        # CIF 实验参考键长（与 ForceField 共用展开规则）
        self.ref_map = _expand_ref_map(mol, ref_bonds)

    def check_bond_violations(self) -> List[Tuple[int, int, float]]:
        violations = []
        for i, j, b in self.mol.bond_list:
            r = self.mol.get_bond_length(i, j) * 100
            key = (i, j) if i < j else (j, i)
            r0 = self.ref_map.get(key)
            if r0 is None:
                r0 = (self.cov_radii.get(self.mol.atoms[i].symbol, 77) +
                      self.cov_radii.get(self.mol.atoms[j].symbol, 77)) + 0.1 * (1 - b)
            if abs(r - r0) / r0 > self.bond_tolerance:
                violations.append((i, j, abs(r - r0) / r0))
        return violations

    def check_angle_violations(self) -> List[Tuple[int, int, int, float]]:
        violations = []
        for i, j, k in self.mol.get_angles():
            theta = math.degrees(self.mol.get_angle(i, j, k))
            hyb = self.mol.atoms[j].hybridization
            theta0 = self.mol.theta0_map.get(hyb, 109.5)
            if abs(theta - theta0) > self.angle_tolerance:
                violations.append((i, j, k, abs(theta - theta0)))
        return violations

    def check_vdw_violations(self) -> List[Tuple[int, int, float]]:
        violations = []
        atoms = self.mol.atoms
        n = len(atoms)
        for i in range(n):
            for j in range(i+1, n):
                if j in atoms[i].neighbors:
                    continue
                if _share_neighbor(atoms, i, j):
                    continue
                r = np.linalg.norm(atoms[i].coords - atoms[j].coords)
                rmin = (self.vdw_radii_ang.get(atoms[i].symbol, 1.70) +
                        self.vdw_radii_ang.get(atoms[j].symbol, 1.70))
                if r < self.vdw_factor * rmin:
                    violations.append((i, j, rmin - r))
        return violations

    def check_valency_violations(self) -> List[Tuple[int, int]]:
        violations = []
        for i, atom in enumerate(self.mol.atoms):
            total_bond = sum(atom.bonds.values())
            nH = atom.n_H
            val = total_bond + nH
            max_val = self.valency_map.get(atom.symbol, 4) + self.valency_tolerance
            if val > max_val:
                violations.append((i, val - max_val))
        return violations

    def constraint_energy(self) -> float:
        E = 0.0
        for _, _, dev in self.check_bond_violations():
            E += 100 * dev ** 2
        for _, _, _, dev_deg in self.check_angle_violations():
            E += 50 * (dev_deg / 10) ** 2
        for _, _, overlap in self.check_vdw_violations():
            E += 200 * (overlap / 10) ** 2
        for _, excess in self.check_valency_violations():
            E += 1000 * excess ** 2
        return E

    def total_violations(self) -> int:
        return (len(self.check_bond_violations()) +
                len(self.check_angle_violations()) +
                len(self.check_vdw_violations()) +
                len(self.check_valency_violations()))

    def is_valid(self) -> bool:
        return self.constraint_energy() < 1.0 and self.total_violations() == 0


# ============================================================
# 5. MDP 环境
# ============================================================

class MDPMolecularOptimization:
    def __init__(self, mol: MoleculeGraph, ref_bonds: Dict = None,
                 error_bonds: Dict = None):
        self.mol = mol
        self.original_positions = mol.get_positions().copy()
        self.ff = ForceField(mol, ref_bonds, error_bonds)
        self.constraint = ConstraintEngine(mol, ref_bonds)
        self.n_atoms = len(mol.atoms)
        self.action_space = list(range(6))
        self.state_dim = 7 * self.n_atoms + 5 * len(mol.bond_list)
        self.gamma = 0.99
        self.done = False

    def reset(self) -> np.ndarray:
        pos = self.original_positions + np.random.normal(0, 0.02, self.original_positions.shape)
        self.mol.set_positions(pos)
        self.done = False
        return self._get_state()

    def reset_from(self, ref_pos: np.ndarray) -> np.ndarray:
        """路径系综：从某副本的最优构象(带扰动)重置，供K副本各自的探索轨迹使用。"""
        pos = ref_pos + np.random.normal(0, 0.02, ref_pos.shape)
        self.mol.set_positions(pos)
        self.done = False
        return self._get_state()

    def _get_state(self) -> np.ndarray:
        atom_feats = []
        for atom in self.mol.atoms:
            hyb_oh = [1 if atom.hybridization == h else 0 for h in ['sp', 'sp2', 'sp3']]
            if not any(hyb_oh):
                hyb_oh = [0, 0, 1]
            atom_feats.extend([atom.Z, atom.q, atom.m, atom.n_H] + hyb_oh)
        bond_feats = []
        for i, j, b in self.mol.bond_list:
            r = self.mol.get_bond_length(i, j)
            same = 1 if self.mol.atoms[i].symbol == self.mol.atoms[j].symbol else 0
            bond_feats.extend([b, r, same, self.mol.atoms[i].Z, self.mol.atoms[j].Z])
        state = np.array(atom_feats + bond_feats, dtype=np.float32)
        return state

    def step(self, action: int) -> Tuple[np.ndarray, float, bool, dict]:
        self._apply_action(action)
        reward = self._compute_reward()
        self.done = self.constraint.is_valid()
        next_state = self._get_state()
        return next_state, reward, self.done, {'action': action}

    def _apply_action(self, action: int):
        pos = self.mol.get_positions()
        n = self.n_atoms
        if action == 0:          # 大随机扰动
            pos += np.random.normal(0, 0.05, pos.shape)
        elif action == 1:        # 小随机扰动
            pos += np.random.normal(0, 0.02, pos.shape)
        elif action == 2:        # 键长修正
            if self.mol.bond_list:
                # 50% 优先修复最差键，50% 随机选键保持探索（与 C++ applyAction 一致）。
                # 优先级 = 错误集惩罚(主导) + 0.0005·键能偏差(同分决胜)——
                # 芳香键的 FF r0 失配(约-17pm)会淹没错误信号，故偏差仅作微弱排序。
                if random.random() < 0.5:
                    worst_p = -1.0
                    sel = 0
                    for bi, (i2, j2, b2) in enumerate(self.mol.bond_list):
                        r_pm = self.mol.get_bond_length(i2, j2) * 100.0
                        dev = r_pm - self.ff.bond_r0_pm(i2, j2, b2)
                        p = 0.0005 * dev * dev
                        key = (i2, j2) if i2 < j2 else (j2, i2)
                        for r_err, w in self.ff.err_map.get(key, []):
                            d = (r_pm - r_err) / 10.0
                            p += w * math.exp(-d * d)
                        if p > worst_p:
                            worst_p, sel = p, bi
                    i, j, b = self.mol.bond_list[sel]
                else:
                    i, j, b = random.choice(self.mol.bond_list)
                r = self.mol.get_bond_length(i, j)
                r0_ang = self.ff.bond_r0_pm(i, j, b) / 100.0
                dr = r0_ang - r
                if abs(dr) > 1e-6:
                    direc = (pos[i] - pos[j]) / (np.linalg.norm(pos[i]-pos[j]) + 1e-8)
                    pos[i] += 0.3 * direc * dr
                    pos[j] -= 0.3 * direc * dr
        elif action == 3:        # 键角修正（简化）
            idx = random.randint(0, n-1)
            pos[idx] += np.random.normal(0, 0.02, 3)
        elif action == 4:        # 中心收缩
            centroid = np.mean(pos, axis=0)
            pos = (pos - centroid) * 0.99 + centroid
        elif action == 5:        # 范德华推开
            pairs = []
            for i in range(n):
                for j in range(i+1, n):
                    if j not in self.mol.atoms[i].neighbors:
                        pairs.append((i, j))
            if pairs:
                i, j = random.choice(pairs)
                r = np.linalg.norm(pos[i] - pos[j])
                rmin = (self.constraint.vdw_radii_ang.get(self.mol.atoms[i].symbol, 1.70) +
                        self.constraint.vdw_radii_ang.get(self.mol.atoms[j].symbol, 1.70))
                overlap = rmin - r
                if overlap > 0:
                    direc = (pos[i] - pos[j]) / (r + 1e-8)
                    pos[i] += 0.5 * direc * overlap
                    pos[j] -= 0.5 * direc * overlap
        self.mol.set_positions(pos)

    def _compute_reward(self) -> float:
        E_ff = self.ff.total_energy()
        E_c = self.constraint.constraint_energy()
        E_err = self.ff.error_penalty()
        V = self.constraint.total_violations()
        n = max(self.n_atoms, 1)
        E_ff_clip = np.clip(E_ff, 0, 1e6)
        E_c_clip = np.clip(E_c, 0, 1e3)
        E_err_clip = np.clip(E_err, 0, 1e3)
        reward = (-0.1 * math.log(1 + E_ff_clip / n) -
                  0.5 * math.log(1 + E_c_clip / n) -
                  2.0 * math.log(1 + E_err_clip / n) -   # 错误集惩罚（与 C++ reward 一致）
                  5.0 * math.log(1 + 10 * V / n))
        reward = np.clip(reward, -50, 50)
        return float(reward)

    def render(self):
        print("当前位置 (Å):")
        print(self.mol.get_positions())
        print(f"总能量: {self.ff.total_energy():.3f}")
        print(f"约束能量: {self.constraint.constraint_energy():.3f}")
        print(f"违规数: {self.constraint.total_violations()}")


# ============================================================
# 6. Q-Learning 训练器
# ============================================================

_NATGRAD_EPS = 1e-8          # 自然梯度对角Fisher分母的平滑项
_ENT_PSEL = 1e-12            # 路径熵的对数保护项（与C++ molopt.cpp一致）

class SimpleQNetwork:
    def __init__(self, state_dim: int, action_dim: int, hidden_size: int = 128):
        self.state_dim = state_dim
        self.action_dim = action_dim
        self.hidden_size = hidden_size
        self.W1 = np.random.randn(state_dim, hidden_size) * np.sqrt(2.0 / state_dim)
        self.b1 = np.zeros(hidden_size)
        self.W2 = np.random.randn(hidden_size, hidden_size) * np.sqrt(2.0 / hidden_size)
        self.b2 = np.zeros(hidden_size)
        self.W3 = np.random.randn(hidden_size, action_dim) * np.sqrt(2.0 / hidden_size)
        self.b3 = np.zeros(action_dim)
        # —— 拓扑流形自然梯度：对角Fisher信息矩阵(RMS)预处理器——
        # 累积参数梯度的平方作为Fisher对角近似的估计，更新步长按 1/sqrt(acc) 缩放，
        # 使梯度方向参数化无关，等价于在黎曼度量上的最速下降(见文档3 §5.2)。
        self.acc_W1 = np.zeros_like(self.W1)
        self.acc_b1 = np.zeros_like(self.b1)
        self.acc_W2 = np.zeros_like(self.W2)
        self.acc_b2 = np.zeros_like(self.b2)
        self.acc_W3 = np.zeros_like(self.W3)
        self.acc_b3 = np.zeros_like(self.b3)

    def forward(self, state: np.ndarray) -> np.ndarray:
        h1 = np.maximum(0, np.dot(state, self.W1) + self.b1)
        h2 = np.maximum(0, np.dot(h1, self.W2) + self.b2)
        q = np.dot(h2, self.W3) + self.b3
        return q

    def predict(self, state: np.ndarray) -> np.ndarray:
        return self.forward(state)

    def softmax_probs(self, state: np.ndarray, tau: float = 1.0) -> np.ndarray:
        """路径系综的动作选择策略：Q值温度softmax（文档2/5的软策略）。"""
        q = self.forward(state) / tau
        q = q - q.max()
        e = np.exp(q)
        return e / e.sum()

    def q_entropy(self, state: np.ndarray, tau: float = 1.0) -> float:
        """路径熵 H[π_θ]：softmax策略的香农熵，抑制过早坍缩到单一动作。"""
        p = self.softmax_probs(state, tau)
        return -float((p * np.log(p + _ENT_PSEL)).sum())

    def update(self, state: np.ndarray, action: int, target: float, lr: float = 0.001):
        h1 = np.maximum(0, np.dot(state, self.W1) + self.b1)
        h2 = np.maximum(0, np.dot(h1, self.W2) + self.b2)
        q_all = np.dot(h2, self.W3) + self.b3
        q = q_all[action]
        dq = 2 * (q - target)

        dW3 = np.zeros_like(self.W3)
        dW3[:, action] = dq * h2
        db3 = np.zeros(self.action_dim)
        db3[action] = dq

        dh2 = dq * self.W3[:, action] * (h2 > 0)
        dW2 = np.outer(h1, dh2)
        db2 = dh2

        dh1 = np.dot(dh2, self.W2.T) * (h1 > 0)
        dW1 = np.outer(state, dh1)
        db1 = dh1

        # 自然梯度更新：acc += g^2（对角Fisher），步长缩放 1/sqrt(acc+eps)
        def _nat(w, acc_w, g):
            acc_w += g * g
            return w - lr * g / (np.sqrt(acc_w) + _NATGRAD_EPS)

        self.W3 = _nat(self.W3, self.acc_W3, dW3)
        self.b3 = _nat(self.b3, self.acc_b3, db3)
        self.W2 = _nat(self.W2, self.acc_W2, dW2)
        self.b2 = _nat(self.b2, self.acc_b2, db2)
        self.W1 = _nat(self.W1, self.acc_W1, dW1)
        self.b1 = _nat(self.b1, self.acc_b1, db1)


class QLearningTrainer:
    def __init__(self, env: MDPMolecularOptimization, state_dim: int, action_dim: int,
                 hidden_size: int = 128, lr: float = 0.001, gamma: float = 0.99,
                 epsilon_start: float = 1.0, epsilon_decay: float = 0.995,
                 epsilon_min: float = 0.01, ensemble_size: int = 4,
                 resample_prob: float = 0.3, tau: float = 1.0,
                 entropy_lambda: float = 0.1):
        self.env = env
        self.q_net = SimpleQNetwork(state_dim, action_dim, hidden_size)
        self.target_net = SimpleQNetwork(state_dim, action_dim, hidden_size)
        self.target_net.W1 = self.q_net.W1.copy()
        self.target_net.b1 = self.q_net.b1.copy()
        self.target_net.W2 = self.q_net.W2.copy()
        self.target_net.b2 = self.q_net.b2.copy()
        self.target_net.W3 = self.q_net.W3.copy()
        self.target_net.b3 = self.q_net.b3.copy()

        self.lr = lr
        self.gamma = gamma
        self.epsilon = epsilon_start
        self.epsilon_decay = epsilon_decay
        self.epsilon_min = epsilon_min
        self.action_dim = action_dim
        # —— 路径系综超参数（见文档5：从单条MEP到路径系综）——
        self.ensemble_size = max(1, int(ensemble_size))   # 并行构象副本数K
        self.resample_prob = resample_prob                # 每轮玻尔兹曼重播种概率
        self.tau = tau                                    # Q-softmax温度
        self.entropy_lambda = entropy_lambda              # 路径熵正则系数

        # 历史最优跟踪（K副本各记一份）：优先有效结构（violations==0），其次总能量最低，
        # 与C++核心(molopt.exe)行为一致，避免输出探索后的偶然劣化构型。
        p0 = env.mol.get_positions().copy()
        s0 = ((0 if env.constraint.is_valid() else 1e12)
              + env.ff.total_energy() + env.ff.error_penalty())
        self._ens_best = [p0.copy() for _ in range(self.ensemble_size)]
        self._ens_score = [s0] * self.ensemble_size

    def _score(self) -> float:
        return ((0 if self.env.constraint.is_valid() else 1e12)
                + self.env.ff.total_energy() + self.env.ff.error_penalty())

    def _track_ensemble(self, k: int):
        score = self._score()
        if score < self._ens_score[k]:
            self._ens_score[k] = score
            self._ens_best[k] = self.env.mol.get_positions().copy()

    def _boltzmann_reseed(self):
        """路径系综耦合：把能量最高的副本按玻尔兹曼锚定重播种到能量最低副本附近，
        等价于对构象空间做并行的退火/漏斗精修，避免K副本各自孤立陷入局部极小。"""
        k_best = int(np.argmin(self._ens_score))
        k_worst = int(np.argmax(self._ens_score))
        if k_best == k_worst:
            return
        anchor = self._ens_best[k_best] + np.random.normal(0, 0.05, self._ens_best[k_best].shape)
        self._ens_best[k_worst] = anchor
        self._ens_score[k_worst] = np.inf  # 占位，下一条轨迹会重新计分
        self.env.mol.set_positions(anchor)

    def select_action(self, state: np.ndarray, tau: float = None) -> int:
        """混合策略：ε-随机探索 或 Q值温度softmax采样（路径系综软策略）。"""
        if tau is None:
            tau = self.tau
        if np.random.rand() < self.epsilon:
            return np.random.randint(self.action_dim)
        p = self.q_net.softmax_probs(state, tau)
        return int(np.random.choice(self.action_dim, p=p))

    def train_step(self, state: np.ndarray, action: int, reward: float,
                   next_state: np.ndarray, done: bool):
        if done:
            target = reward
        else:
            next_q = self.target_net.predict(next_state)
            target = reward + self.gamma * np.max(next_q)
        self.q_net.update(state, action, target, self.lr)

    def sync_target(self):
        self.target_net.W1 = self.q_net.W1.copy()
        self.target_net.b1 = self.q_net.b1.copy()
        self.target_net.W2 = self.q_net.W2.copy()
        self.target_net.b2 = self.q_net.b2.copy()
        self.target_net.W3 = self.q_net.W3.copy()
        self.target_net.b3 = self.q_net.b3.copy()

    def train_episode(self, max_steps: int = 100) -> float:
        """一个回合内依次推进K个系综副本，共享同一Q网络（离策略Q学习天然支持）。
        每段奖励叠加路径熵正则；段末按概率做玻尔兹曼重播种。"""
        total_reward = 0.0
        for k in range(self.ensemble_size):
            state = self.env.reset_from(self._ens_best[k])
            for _ in range(max_steps):
                action = self.select_action(state)
                next_state, reward, done, _ = self.env.step(action)
                reward = reward + self.entropy_lambda * self.q_net.q_entropy(next_state, self.tau)
                self._track_ensemble(k)
                self.train_step(state, action, reward, next_state, done)
                total_reward += reward
                state = next_state
                if done:
                    break
                if np.random.rand() < 0.1:
                    self.sync_target()
        self._boltzmann_reseed()
        self.epsilon = max(self.epsilon_min, self.epsilon * self.epsilon_decay)
        return total_reward

    def train(self, episodes: int = 10, max_steps: int = 100):
        for ep in range(episodes):
            total_r = self.train_episode(max_steps)
            print(f"Episode {ep+1}/{episodes}: total reward = {total_r:.3f}, "
                  f"epsilon = {self.epsilon:.3f}, K = {self.ensemble_size}")
            if self.env.done:
                print("找到有效结构！")
                break
        # 训练结束后回到系综内历史最优构型（而非探索结束时的末态）
        k_best = int(np.argmin(self._ens_score))
        self.env.mol.set_positions(self._ens_best[k_best])
        self.env.done = self.env.constraint.is_valid()


# ============================================================
# 7. 可视化函数（球棍模型 + 氢原子控制）
# ============================================================

def molecule_graph_to_rdkit(mol_graph: MoleculeGraph) -> Chem.rdchem.Mol:
    """将 MoleculeGraph 转换为 RDKit Mol 对象（包含坐标）"""
    rdmol = Chem.RWMol()
    for atom in mol_graph.atoms:
        rd_atom = Chem.Atom(atom.symbol)
        rd_atom.SetFormalCharge(atom.q)
        rdmol.AddAtom(rd_atom)
    for i, j, b in mol_graph.bond_list:
        bt = Chem.rdchem.BondType.SINGLE
        if b == 2.0:
            bt = Chem.rdchem.BondType.DOUBLE
        elif b == 3.0:
            bt = Chem.rdchem.BondType.TRIPLE
        elif b == 1.5:
            bt = Chem.rdchem.BondType.AROMATIC
        rdmol.AddBond(i, j, bt)
    conf = Chem.Conformer(mol_graph.n)
    pos = mol_graph.get_positions()
    for i, (x, y, z) in enumerate(pos):
        conf.SetAtomPosition(i, (float(x), float(y), float(z)))
    rdmol.AddConformer(conf)
    return rdmol.GetMol()


def visualize_molecule_3d(mol_graph: MoleculeGraph, title: str = "Molecular Structure",
                          show_hydrogen: bool = True):
    """
    使用 py3Dmol 显示球棍模型，可通过 show_hydrogen 控制氢原子显示。
    改用 MolBlock 格式，确保氢原子被保留。
    """
    rdmol = molecule_graph_to_rdkit(mol_graph)
    
    # ---- 调试输出 ----
    atom_symbols = [atom.GetSymbol() for atom in rdmol.GetAtoms()]
    print(f"总原子数: {len(atom_symbols)}，原子符号: {atom_symbols}")
    if not show_hydrogen:
        rdmol = Chem.RemoveHs(rdmol)
        print(f"移除氢后原子数: {rdmol.GetNumAtoms()}")
    else:
        print("保持所有原子（含氢）")

    try:
        import py3Dmol
        # 使用 MolBlock 代替 PDB
        mol_block = Chem.MolToMolBlock(rdmol)
        viewer = py3Dmol.view(width=800, height=600)
        viewer.addModel(mol_block, 'mol')
        # 球棍模型，使用元素颜色，球稍大
        viewer.setStyle({
            'sphere': {'colorscheme': 'element', 'scale': 0.7},
            'stick': {'colorscheme': 'element', 'radius': 0.15}
        })
        viewer.setBackgroundColor('lightgray')   # 浅灰背景让氢白色更明显
        viewer.zoomTo()
        html_file = "molecule_view.html"
        viewer.write_html(html_file)
        webbrowser.open(html_file)
        print(f"分子结构已显示在浏览器中。")
        input("按 Enter 关闭可视化窗口并删除临时文件...")
        if os.path.exists(html_file):
            os.remove(html_file)
    except ImportError:
        print("py3Dmol 未安装，回退到 Matplotlib 静态图。")
        _visualize_matplotlib(mol_graph, title)
    except Exception as e:
        print(f"py3Dmol 显示失败: {e}，回退到 Matplotlib。")
        _visualize_matplotlib(mol_graph, title)


def _visualize_matplotlib(mol_graph: MoleculeGraph, title: str = "Molecular Structure"):
    """Matplotlib 静态 3D 图（备选）"""
    try:
        import matplotlib.pyplot as plt
        from mpl_toolkits.mplot3d import Axes3D
        pos = mol_graph.get_positions()
        symbols = [atom.symbol for atom in mol_graph.atoms]
        colors = {'H': 'white', 'C': 'black', 'O': 'red', 'N': 'blue',
                  'F': 'green', 'Cl': 'green', 'Br': 'brown', 'I': 'purple'}
        fig = plt.figure(figsize=(8, 6))
        ax = fig.add_subplot(111, projection='3d')
        for i, (x, y, z) in enumerate(pos):
            color = colors.get(symbols[i], 'gray')
            ax.scatter(x, y, z, s=200, c=color, edgecolors='k', alpha=0.8)
            ax.text(x, y, z, symbols[i], fontsize=10, ha='center', va='bottom')
        for i, j, _ in mol_graph.bond_list:
            p1 = pos[i]
            p2 = pos[j]
            ax.plot([p1[0], p2[0]], [p1[1], p2[1]], [p1[2], p2[2]], 'k-', lw=2)
        ax.set_title(title)
        ax.set_xlabel('X (Å)')
        ax.set_ylabel('Y (Å)')
        ax.set_zlabel('Z (Å)')
        plt.show()
    except ImportError:
        print("Matplotlib 未安装，无法显示 3D 图。")

# ============================================================
# 8. Tkinter 氢原子显示开关
# ============================================================

def ask_show_hydrogen() -> bool:
    root = tk.Tk()
    root.title("显示选项")
    root.geometry("300x120")
    root.resizable(False, False)

    var = tk.BooleanVar(value=True)
    check = ttk.Checkbutton(root, text="显示氢原子", variable=var)
    check.pack(pady=20)

    result = True
    def on_ok():
        nonlocal result
        result = var.get()
        root.destroy()

    btn = ttk.Button(root, text="确认", command=on_ok)
    btn.pack(pady=5)
    root.mainloop()
    return result


# ========== 在 mol_optimizer.py 末尾添加 ==========

def generate_molecule_html(mol_graph: MoleculeGraph, title: str = "Molecular Structure",
                           show_hydrogen: bool = True, output_file: str = "molecule.html") -> str:
    """
    生成分子 3D 结构的 HTML 文件（球棍模型），并返回文件路径。
    """
    rdmol = molecule_graph_to_rdkit(mol_graph)
    if not show_hydrogen:
        rdmol = Chem.RemoveHs(rdmol)

    try:
        import py3Dmol
        mol_block = Chem.MolToMolBlock(rdmol)
        viewer = py3Dmol.view(width=800, height=600)
        viewer.addModel(mol_block, 'mol')
        viewer.setStyle({
            'sphere': {'colorscheme': 'element', 'scale': 0.7},
            'stick': {'colorscheme': 'element', 'radius': 0.15}
        })
        viewer.setBackgroundColor('lightgray')
        viewer.zoomTo()
        viewer.write_html(output_file)
        return output_file
    except ImportError:
        print("py3Dmol 未安装，无法生成 HTML。请安装：pip install py3Dmol")
        return None
    except Exception as e:
        print(f"生成 HTML 失败: {e}")
        return None


# ========== C++ 加速核心接口 ==========

def _export_molopt_input(mol_graph: MoleculeGraph, path: str,
                         episodes: int, max_steps: int, seed=None,
                         ensemble_size: int = 4, resample_prob: float = 0.3,
                         tau: float = 1.0, entropy_lambda: float = 0.1,
                         ref_bonds: Dict = None, error_bonds: Dict = None):
    """把分子图导出为 molopt.exe 的行式输入文件（%.17g 保证精度往返）"""
    lines = [f"NATOMS {mol_graph.n}"]
    for a in mol_graph.atoms:
        c = a.coords
        lines.append(f"{a.symbol} {a.q} {a.Z} {c[0]:.17g} {c[1]:.17g} {c[2]:.17g}")
    lines.append(f"NBONDS {len(mol_graph.bond_list)}")
    for i, j, b in mol_graph.bond_list:
        lines.append(f"{i} {j} {b:.17g}")
    # CIF 实验参考键长：按具体键写出 r0（pm），覆盖 C++ 端共价半径求和
    ref_map = _expand_ref_map(mol_graph, ref_bonds)
    for (i, j), r0_pm in sorted(ref_map.items()):
        lines.append(f"REFBOND {i} {j} {r0_pm:.17g}")
    # 错误集键长（二重态错误建模特征）：按具体键写出 (r_err_pm, w)
    err_map = _expand_error_map(mol_graph, error_bonds)
    for (i, j), terms in sorted(err_map.items()):
        for r_err_pm, w in terms:
            lines.append(f"ERRBOND {i} {j} {r_err_pm:.17g} {w:.17g}")
    lines.append(f"EPISODES {episodes}")
    lines.append(f"STEPS {max_steps}")
    if seed is not None:
        lines.append(f"SEED {seed}")
    lines.append(f"ENSEMBLE {ensemble_size}")
    lines.append(f"RESAMPLE {resample_prob:.17g}")
    lines.append(f"TAU {tau:.17g}")
    lines.append(f"ENTROPY {entropy_lambda:.17g}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def _run_native_opt(mol_graph: MoleculeGraph, episodes: int = 30,
                    max_steps: int = 60, seed=None, ensemble_size: int = 4,
                    resample_prob: float = 0.3, tau: float = 1.0,
                    entropy_lambda: float = 0.1, ref_bonds: Dict = None,
                    error_bonds: Dict = None) -> Dict:
    """
    调用C++计算核心(molopt.exe)执行MDP优化（路径系综+自然梯度+路径熵）。
    成功时把优化后坐标写回mol_graph，返回结果dict；失败抛异常。
    ref_bonds: CIF 实验参考键长 {("Al","Se"): 2.1935, ...}（Å），优先于内置力场参数。
    error_bonds: 错误集键长 {("Al","C"): [{"r": 1.9304, "w": 10.0}, ...]}（Å），
                逼近已知错误键长时施加高斯惩罚（错误惩罚机制）。
    """
    fd, path = tempfile.mkstemp(suffix=".txt", prefix="molopt_")
    os.close(fd)
    try:
        _export_molopt_input(mol_graph, path, episodes, max_steps, seed,
                             ensemble_size, resample_prob, tau, entropy_lambda,
                             ref_bonds, error_bonds)
        r = subprocess.run([MOLOPT_EXE, path], capture_output=True,
                           text=True, timeout=600,
                           encoding="utf-8", errors="replace")
        if r.returncode != 0:
            raise RuntimeError(f"molopt.exe退出码 {r.returncode}: {r.stderr[:200]}")
        res = json.loads(r.stdout)
        if res.get("mode") != "optimize":
            raise RuntimeError(f"molopt.exe返回意外模式: {res.get('mode')}")
        pos = np.array(res["coords"], dtype=float)
        if pos.shape != (mol_graph.n, 3):
            raise RuntimeError(f"坐标形状不符: {pos.shape}")
        mol_graph.set_positions(pos)
        return res
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def run_optimization(smiles: str, show_hydrogen: bool = True,
                     episodes: int = 30, max_steps: int = 60,
                     output_html: str = "optimized_molecule.html",
                     use_native: bool = True, seed=None,
                     ensemble_size: int = 4, resample_prob: float = 0.3,
                     tau: float = 1.0, entropy_lambda: float = 0.1,
                     ref_bonds: Dict = None,
                     ref_xyz: str = None,
                     error_bonds: Dict = None) -> dict:
    """
    运行完整的分子优化流程，返回结果字典。

    优先使用C++计算核心(molopt.exe)，不可用时自动回退Python实现。

    新增优化（文档2/3/5：路径系综＋自然梯度＋路径熵）：
        ensemble_size: 路径系综并行构象副本数K
        resample_prob: 每轮玻尔兹曼重播种概率
        tau:          Q值温度softmax的温度
        entropy_lambda: 路径熵正则系数

    实验结构修正（CIF）：
        ref_bonds: 实验参考键长 {("Al","Se"): 2.1935, ...}（Å，键也可写 "Al-Se"），
                   优先于内置共价半径参数；可用 ref_bonds_from_xyz() 从 CIF 解析
                   产物 XYZ 中提取。
        ref_xyz:   实验结构 XYZ 路径；给出时自动提取 EXOTIC_ELEMENTS 相关键长
                   作为 ref_bonds（显式 ref_bonds 优先）。

    错误集惩罚（二重态错误建模特征）：
        error_bonds: 错误键长 {("Al","C"): [{"r": 1.9304, "w": 10.0}, ...]}（Å），
                     键长逼近已知错误值时施加高斯惩罚（ERRBOND 机制），
                     与 ref_bonds 正集配合：推向实验键长、避开错误键长。

    参数:
        smiles: SMILES 字符串
        show_hydrogen: 是否在可视化中显示氢原子
        episodes: 训练轮数
        max_steps: 每轮最大步数
        output_html: 输出 HTML 文件名
        use_native: 是否优先使用C++加速核心
        seed: C++核心随机种子（None=随机；仅C++路径生效）

    返回:
        dict: {
            'smiles': str,
            'total_energy': float,
            'constraint_energy': float,
            'violations': int,
            'is_valid': bool,
            'html_path': str or None,
            'backend': 'cpp' | 'python',
            'elapsed_ms': float,
            'episodes_run': int,
            'episode_rewards': list[float]
        }
    """
    # 1. 构建分子图
    mol_graph = smiles_to_molecule_graph(smiles, add_hs=True)

    # 1.5 实验结构修正（CIF）：从实验 XYZ 提取参考键长
    if ref_bonds is None and ref_xyz:
        ref_bonds = ref_bonds_from_xyz(ref_xyz)

    import time as _time
    t0 = _time.perf_counter()
    backend = "python"
    episodes_run = episodes
    ep_rewards = []

    # 2. 优先C++核心
    if use_native and os.path.exists(MOLOPT_EXE):
        try:
            native = _run_native_opt(mol_graph, episodes, max_steps, seed,
                                     ensemble_size, resample_prob, tau, entropy_lambda,
                                     ref_bonds, error_bonds)
            backend = "cpp"
            en = native["energies"]
            total_energy = en["total"]
            constraint_energy = en["constraint_energy"]
            violations = en["violations"]
            is_valid = en["is_valid"]
            error_penalty = en.get("error_penalty", 0.0)
            episodes_run = native.get("episodes_run", episodes)
            ep_rewards = native.get("episode_rewards", [])
            elapsed = native.get("elapsed_ms", 0.0)
            print(f"C++核心优化完成: {episodes_run}轮, 用时 {elapsed:.1f}ms")
        except Exception as e:
            print(f"C++加速核心不可用({e})，回退Python实现")
            backend = "python"

    # 3. Python回退路径
    if backend == "python":
        env = MDPMolecularOptimization(mol_graph, ref_bonds, error_bonds)
        trainer = QLearningTrainer(env, env.state_dim, len(env.action_space),
                                   hidden_size=64, lr=0.01, gamma=0.99,
                                   epsilon_start=1.0, epsilon_decay=0.9,
                                   epsilon_min=0.05, ensemble_size=ensemble_size,
                                   resample_prob=resample_prob, tau=tau,
                                   entropy_lambda=entropy_lambda)
        print(f"\n开始优化分子: {smiles} (训练 {episodes} 轮, 每轮 {max_steps} 步, "
              f"路径系综K={ensemble_size})")
        trainer.train(episodes=episodes, max_steps=max_steps)
        total_energy = env.ff.total_energy()
        constraint_energy = env.constraint.constraint_energy()
        violations = env.constraint.total_violations()
        is_valid = env.constraint.is_valid()
        error_penalty = env.ff.error_penalty()

    elapsed_ms = (_time.perf_counter() - t0) * 1000.0

    # 4. 生成 3D 结构 HTML
    html_path = generate_molecule_html(mol_graph, title=f"Optimized: {smiles}",
                                       show_hydrogen=show_hydrogen,
                                       output_file=output_html)

    # 5. 收集结果
    return {
        'smiles': smiles,
        'total_energy': total_energy,
        'constraint_energy': constraint_energy,
        'error_penalty': error_penalty,
        'violations': violations,
        'is_valid': is_valid,
        'html_path': html_path,
        'backend': backend,
        'elapsed_ms': elapsed_ms,
        'episodes_run': episodes_run,
        'episode_rewards': ep_rewards,
        'ref_bonds': normalize_ref_bonds(ref_bonds) if ref_bonds else None,
        'error_bonds': normalize_error_bonds(error_bonds) if error_bonds else None
    }

# ============================================================
# 9. 主程序
# ============================================================

def main():
    # 选择分子（可替换为其他 SMILES）
    smiles = "CCO"   # 乙醇
    print(f"加载分子: {smiles}")

    # 构建分子图（带氢）
    mol_graph = smiles_to_molecule_graph(smiles, add_hs=True)
    print(f"原子数: {mol_graph.n}, 键数: {len(mol_graph.bond_list)}")
    print(f"状态维度: {mol_graph.n*7 + len(mol_graph.bond_list)*5}")

    # 保存初始结构
    initial_mol_graph = MoleculeGraph(mol_graph.atoms, mol_graph.bond_list)
    initial_mol_graph.set_positions(mol_graph.get_positions().copy())

    # 创建 MDP 环境
    env = MDPMolecularOptimization(mol_graph)

    # 创建训练器
    trainer = QLearningTrainer(env, env.state_dim, len(env.action_space),
                               hidden_size=64, lr=0.01, gamma=0.99,
                               epsilon_start=1.0, epsilon_decay=0.9,
                               epsilon_min=0.05)

    print("\n开始训练...")
    trainer.train(episodes=30, max_steps=60)

    print("\n优化后的分子结构:")
    env.render()
    if env.constraint.is_valid():
        print("优化成功：满足所有约束！")
    else:
        print("优化未完全满足约束，可继续训练或调整超参数。")

    # 询问是否显示氢原子
    show_h = ask_show_hydrogen()

    print("\n显示初始分子结构（球棍模型）...")
    visualize_molecule_3d(initial_mol_graph, title="Initial Structure", show_hydrogen=show_h)

    print("\n显示优化后分子结构（球棍模型）...")
    visualize_molecule_3d(mol_graph, title="Optimized Structure", show_hydrogen=show_h)


if __name__ == "__main__":
    main()