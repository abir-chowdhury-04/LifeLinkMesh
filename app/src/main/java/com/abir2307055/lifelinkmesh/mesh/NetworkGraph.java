package com.abir2307055.lifelinkmesh.mesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Keeps what this phone knows about the mesh: neighbour reports from other phones,
// and which links a message just used.
public class NetworkGraph {

    // One link as it is drawn.
    public static class Edge {
        public final String a;
        public final String b;
        public final boolean lit;   // true if a message passed over it a moment ago

        Edge(String a, String b, boolean lit) {
            this.a = a;
            this.b = b;
            this.lit = lit;
        }
    }

    // Everything the view needs to draw one frame.
    public static class Snapshot {
        public final String me;
        public final List<String> nodes;   // "me" is always first
        public final List<Edge> edges;

        Snapshot(String me, List<String> nodes, List<Edge> edges) {
            this.me = me;
            this.nodes = nodes;
            this.edges = edges;
        }
    }

    private static final long REPORT_MEMORY_MS = 30_000;   // forget a phone's report after 30 s
    private static final long LIT_MS = 2_500;              // highlight lasts 2.5 s

    private static class Report {
        final Set<String> neighbors;
        final long receivedAt;

        Report(List<String> neighbors) {
            this.neighbors = new HashSet<>(neighbors);
            this.receivedAt = System.currentTimeMillis();
        }
    }

    // origin phone name -> its latest list of neighbours
    private final Map<String, Report> reports = new HashMap<>();
    // link key -> time until which it is highlighted
    private final Map<String, Long> litUntil = new HashMap<>();

    private static String key(String x, String y) {
        return x.compareTo(y) <= 0 ? x + "\u0001" + y : y + "\u0001" + x;
    }

    // A phone told us who it is linked to.
    public synchronized void setReport(String origin, List<String> neighbors) {
        reports.put(origin, new Report(neighbors));
    }

    // A message just used this link: highlight it.
    public synchronized void light(String x, String y) {
        if (!x.equals(y)) {
            litUntil.put(key(x, y), System.currentTimeMillis() + LIT_MS);
        }
    }

    // A message arrived with this path (origin first): highlight every hop,
    // including the last hop to me.
    public synchronized void lightPath(List<String> path, String me) {
        List<String> full = new ArrayList<>(path);
        full.add(me);
        for (int i = 0; i < full.size() - 1; i++) {
            light(full.get(i), full.get(i + 1));
        }
    }

    public synchronized void clear() {
        reports.clear();
        litUntil.clear();
    }

    // Builds what to draw. myNeighbours = the phones I am connected to right now.
    public synchronized Snapshot snapshot(String me, List<String> myNeighbours) {
        long now = System.currentTimeMillis();
        reports.values().removeIf(r -> now - r.receivedAt > REPORT_MEMORY_MS);
        litUntil.values().removeIf(until -> until < now);

        Set<String> nodes = new LinkedHashSet<>();
        nodes.add(me);
        List<Edge> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // My own links come only from my live connection list, so they are always correct.
        for (String neighbour : myNeighbours) {
            addEdge(nodes, edges, seen, me, neighbour, now);
        }
        // Other phones' links come from their reports.
        for (Map.Entry<String, Report> entry : reports.entrySet()) {
            String origin = entry.getKey();
            if (origin.equals(me)) {
                continue;
            }
            for (String neighbour : entry.getValue().neighbors) {
                if (neighbour.equals(me)) {
                    continue;   // a link to me is only believed if I have it myself
                }
                addEdge(nodes, edges, seen, origin, neighbour, now);
            }
        }
        return new Snapshot(me, new ArrayList<>(nodes), edges);
    }

    private void addEdge(Set<String> nodes, List<Edge> edges, Set<String> seen,
                         String a, String b, long now) {
        String k = key(a, b);
        if (!seen.add(k)) {
            return;   // already added from the other end
        }
        nodes.add(a);
        nodes.add(b);
        Long until = litUntil.get(k);
        edges.add(new Edge(a, b, until != null && until > now));
    }
}