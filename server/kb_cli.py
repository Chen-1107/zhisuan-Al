#!/usr/bin/env python3
"""智算铝宾知识库命令行工具"""

import sys
import argparse
import os
from knowledge_base import KnowledgeBase

def main():
    parser = argparse.ArgumentParser(description="智算铝宾知识库命令行工具")
    parser.add_argument("--list", "-l", action="store_true", help="列出所有技能")
    parser.add_argument("--category", "-c", type=str, help="按分类过滤（如 dft）")
    parser.add_argument("--search", "-s", type=str, help="搜索关键词")
    parser.add_argument("--show", "-g", type=str, help="显示指定技能内容（根据文件名）")
    parser.add_argument("--dir", "-d", type=str, default=None, help="skills 目录路径（默认自动查找）")
    args = parser.parse_args()

    # 初始化知识库（自动加载）
    if args.dir:
        kb = KnowledgeBase(args.dir)
    else:
        kb = KnowledgeBase()  # 自动查找并加载

    # 检查是否加载成功
    if not kb._loaded:
        print("知识库加载失败，请检查目录路径。")
        sys.exit(1)

    # 执行命令
    if args.list:
        skills = kb.list_skills(args.category)
        if not skills:
            print("没有找到任何技能。请确保 skills 目录下存在 .md 文件。")
            return
        for skill in skills:
            print(f"[{skill.category}] {skill.title} ({skill.name})")
        print(f"共 {len(skills)} 个技能")
    elif args.search:
        results = kb.search_skills(args.search)
        if not results:
            print(f"未找到包含 '{args.search}' 的技能")
            return
        for skill in results:
            print(f"[{skill.category}] {skill.title} ({skill.name})")
        print(f"找到 {len(results)} 个匹配")
    elif args.show:
        skill = kb.get_skill(args.show)
        if skill:
            print(skill.content)
        else:
            print(f"未找到技能: {args.show}")
    else:
        parser.print_help()

if __name__ == "__main__":
    main()