package org.reactome.server.diagram.converter.qa.diagram;

import org.reactome.server.diagram.converter.layout.output.Diagram;
import org.reactome.server.diagram.converter.layout.output.Node;
import org.reactome.server.diagram.converter.qa.common.DiagramTest;
import org.reactome.server.diagram.converter.utils.TestReportsHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 * @author Kostas Sidiropoulos <ksidiro@ebi.ac.uk>
 */
@SuppressWarnings("unused")
@DiagramTest
public class OverlappingEntities implements DiagramQA {

    private static final List<String> lines = new ArrayList<>();

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getDescription() {
        return "Detects pairs of entities that physically overlap in the diagram.";
    }

    @Override
    public List<String> getReport() {
        if (!lines.isEmpty())
            lines.add(0, "Diagram,DiagramName,Entity1,Entity1_Name,Entity2,Entity2_Name,Created,Modified");
        return lines;
    }

    @Override
    public void run(Diagram diagram) {
        for (Node node1 : diagram.getNodes()) {
            for (Node node2 : diagram.getNodes()) {
                boolean node1Fadeout = node1.isFadeOut != null && node1.isFadeOut;
                boolean node2Fadeout = node2.isFadeOut != null && node2.isFadeOut;
                if (node1.reactomeId < node2.reactomeId && !node1Fadeout && !node2Fadeout) {
                    if (node1.overlaps(node2)) {
                        lines.add(String.format("%s,\"%s\",%s,\"%s\",%s,\"%s\",%s",
                                diagram.getStableId(),
                                diagram.getDisplayName(),
                                node1.reactomeId,
                                node1.displayName,
                                node2.reactomeId,
                                node2.displayName,
                                TestReportsHelper.getCreatedModified(node2.reactomeId)));
                    }
                }
            }
        }
    }
}
