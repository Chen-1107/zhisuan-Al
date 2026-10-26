#!/usr/bin/env python3
"""
工作流引擎 - 解析DAG，调度执行
"""

import networkx as nx
from typing import Dict, Any, List, Optional
import asyncio

class WorkflowEngine:
    def __init__(self):
        self.modules = {}
        self.workflows = {}
        self.running_jobs = {}

    def register_module(self, name: str, module):
        self.modules[name] = module

    def create_workflow(self, workflow_id: str, nodes: List[Dict], connections: List[Dict]) -> str:
        graph = nx.DiGraph()
        for node in nodes:
            graph.add_node(node['id'], **node)
        for conn in connections:
            graph.add_edge(conn['source'], conn['target'])

        if not nx.is_directed_acyclic_graph(graph):
            raise ValueError("工作流包含循环依赖")

        self.workflows[workflow_id] = {
            "nodes": nodes,
            "connections": connections,
            "graph": graph,
            "topological_order": list(nx.topological_sort(graph)),
            "status": "created"
        }
        return workflow_id

    async def execute_workflow(self, workflow_id: str, inputs: Dict[str, Any]) -> Dict:
        workflow = self.workflows.get(workflow_id)
        if not workflow:
            raise ValueError(f"工作流不存在: {workflow_id}")

        workflow["status"] = "running"
        results = {}

        for node_id in workflow["topological_order"]:
            node = next(n for n in workflow["nodes"] if n['id'] == node_id)
            module_name = node.get('module')
            node_type = node.get('type')

            # 收集输入
            node_inputs = {}
            for conn in workflow["connections"]:
                if conn['target'] == node_id:
                    source_result = results.get(conn['source'], {})
                    source_output = source_result.get(conn.get('source_port', ''))
                    node_inputs[conn.get('target_port', '')] = source_output

            if node_id in inputs:
                node_inputs.update(inputs[node_id])

            module = self.modules.get(module_name)
            if module:
                result = await module.execute_node(
                    node_type,
                    node_inputs,
                    node.get('config', {})
                )
                results[node_id] = result
            else:
                results[node_id] = {"error": f"模块 {module_name} 未注册"}

        workflow["status"] = "completed"
        return results

    def get_workflow_status(self, workflow_id: str) -> str:
        return self.workflows.get(workflow_id, {}).get("status", "unknown")

    def export_workflow(self, workflow_id: str) -> Dict:
        wf = self.workflows.get(workflow_id)
        if not wf:
            return {}
        return {
            "id": workflow_id,
            "nodes": wf["nodes"],
            "connections": wf["connections"]
        }

    def import_workflow(self, data: Dict) -> str:
        return self.create_workflow(
            data.get("id", "imported"),
            data.get("nodes", []),
            data.get("connections", [])
        )