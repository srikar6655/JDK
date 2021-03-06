/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package org.graalvm.compiler.loop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.EconomicSet;
import org.graalvm.util.Equivalence;

public class LoopsData {
    private final EconomicMap<LoopBeginNode, LoopEx> loopBeginToEx = EconomicMap.create(Equivalence.IDENTITY);
    private final ControlFlowGraph cfg;
    private final List<LoopEx> loops;

    @SuppressWarnings("try")
    public LoopsData(final StructuredGraph graph) {
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("ControlFlowGraph")) {
            cfg = ControlFlowGraph.compute(graph, true, true, true, true);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        assert checkLoopOrder(cfg.getLoops());
        loops = new ArrayList<>(cfg.getLoops().size());
        for (Loop<Block> loop : cfg.getLoops()) {
            LoopEx ex = new LoopEx(loop, this);
            loops.add(ex);
            loopBeginToEx.put(ex.loopBegin(), ex);
        }
    }

    /**
     * Checks that loops are ordered such that outer loops appear first.
     */
    private static boolean checkLoopOrder(Iterable<Loop<Block>> loops) {
        EconomicSet<Loop<Block>> seen = EconomicSet.create(Equivalence.IDENTITY);
        for (Loop<Block> loop : loops) {
            if (loop.getParent() != null && !seen.contains(loop.getParent())) {
                return false;
            }
            seen.add(loop);
        }
        return true;
    }

    public LoopEx loop(Loop<Block> loop) {
        return loopBeginToEx.get((LoopBeginNode) loop.getHeader().getBeginNode());
    }

    public LoopEx loop(LoopBeginNode loopBegin) {
        return loopBeginToEx.get(loopBegin);
    }

    public List<LoopEx> loops() {
        return loops;
    }

    public List<LoopEx> outerFirst() {
        return loops;
    }

    public Collection<LoopEx> countedLoops() {
        List<LoopEx> counted = new LinkedList<>();
        for (LoopEx loop : loops()) {
            if (loop.isCounted()) {
                counted.add(loop);
            }
        }
        return counted;
    }

    public void detectedCountedLoops() {
        for (LoopEx loop : loops()) {
            loop.detectCounted();
        }
    }

    public ControlFlowGraph getCFG() {
        return cfg;
    }

    public InductionVariable getInductionVariable(ValueNode value) {
        InductionVariable match = null;
        for (LoopEx loop : loops()) {
            InductionVariable iv = loop.getInductionVariables().get(value);
            if (iv != null) {
                if (match != null) {
                    return null;
                }
                match = iv;
            }
        }
        return match;
    }

    /**
     * Deletes any nodes created within the scope of this object that have no usages.
     */
    public void deleteUnusedNodes() {
        for (LoopEx loop : loops()) {
            loop.deleteUnusedNodes();
        }
    }
}
