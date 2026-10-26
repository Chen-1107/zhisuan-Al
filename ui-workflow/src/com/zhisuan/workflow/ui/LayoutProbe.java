package com.zhisuan.workflow.ui;

import com.zhisuan.workflow.model.WorkflowGraph;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/** 布局自检：打印各区域 bounds，验证 SOUTH 是否被分配空间。 */
public class LayoutProbe {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WorkflowGraph graph = WorkflowGraph.demoGraph();
            WorkflowCanvas canvas = new WorkflowCanvas(graph);

            JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
            toolbar.setBackground(Theme.PANEL);
            toolbar.add(new JButton("适应视图"));

            JLabel statsLabel = new JLabel("统计测试");
            statsLabel.setFont(Theme.uiFont(11));

            JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
            bottomBar.setBackground(Theme.PANEL);
            bottomBar.add(statsLabel);
            bottomBar.add(new JButton("模拟推进"));

            JLabel infoLabel = new JLabel("节点 8 · 连线 8");
            JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5));
            statusBar.setBackground(Theme.PANEL);
            statusBar.add(infoLabel);

            JPanel south = new JPanel(new BorderLayout());
            south.setBackground(Theme.PANEL);
            south.add(bottomBar, BorderLayout.NORTH);
            south.add(statusBar, BorderLayout.SOUTH);

            JFrame frame = new JFrame("LayoutProbe");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 720);
            frame.setLayout(new BorderLayout());
            frame.add(toolbar, BorderLayout.NORTH);
            frame.add(canvas, BorderLayout.CENTER);
            frame.add(south, BorderLayout.SOUTH);
            frame.setVisible(true);

            frame.validate();
            System.out.println("toolbar  = " + toolbar.getBounds());
            System.out.println("canvas   = " + canvas.getBounds());
            System.out.println("bottomBar= " + bottomBar.getBounds() + " prefH=" + bottomBar.getPreferredSize().height);
            System.out.println("statusBar= " + statusBar.getBounds() + " prefH=" + statusBar.getPreferredSize().height);
            System.out.println("south    = " + south.getBounds() + " prefH=" + south.getPreferredSize().height);
            System.out.println("content  = " + frame.getContentPane().getSize());
            System.exit(0);
        });
    }
}
