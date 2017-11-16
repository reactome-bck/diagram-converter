package org.reactome.server.diagram.converter.qa.diagram;

import org.reactome.server.diagram.converter.qa.common.ConverterReport;
import org.reactome.server.diagram.converter.utils.TestReportsHelper;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.utils.ReactomeGraphCore;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kostas Sidiropoulos <ksidiro@ebi.ac.uk>
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 */
@SuppressWarnings("unused")
@ConverterReport
public class RenderableClassMismatch implements DiagramQA {

    private static final AdvancedDatabaseObjectService ads = ReactomeGraphCore.getService(AdvancedDatabaseObjectService.class);

    private static final List<String> lines = new ArrayList<>();

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getDescription() {
        return "Detects diagrams containing entities with a mismatch in their annotated RenderableClass.";
    }

    @Override
    public List<String> getReport() {
        if (!lines.isEmpty()) lines.add(0, "Diagram,DiagramName,Entity,EntitySchemaClass,EntityName,RenderableClass,SuggestedRenderableClass,Created,Modified");
        return lines;
    }

    public static void add(String diagramStId, String diagramName, Long entityId, String schemaClass, String entityName,String wrongRC, String rightRC){
        lines.add(String.format("%s,\"%s\",%d,%s,\"%s\",%s,%s,%s",
                diagramStId,
                diagramName,
                entityId,
                schemaClass,
                entityName,
                wrongRC,
                rightRC,
                TestReportsHelper.getCreatedModified(entityId)
        ));
    }

}