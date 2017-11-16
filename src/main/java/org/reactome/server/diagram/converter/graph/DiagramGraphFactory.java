package org.reactome.server.diagram.converter.graph;

import org.reactome.server.diagram.converter.graph.output.EntityNode;
import org.reactome.server.diagram.converter.graph.output.EventNode;
import org.reactome.server.diagram.converter.graph.output.Graph;
import org.reactome.server.diagram.converter.graph.output.SubpathwayNode;
import org.reactome.server.diagram.converter.graph.query.EdgesQueryResult;
import org.reactome.server.diagram.converter.graph.query.NodesQueryResult;
import org.reactome.server.diagram.converter.graph.query.QueryResult;
import org.reactome.server.diagram.converter.graph.query.SubpathwaysQueryResult;
import org.reactome.server.diagram.converter.layout.output.Diagram;
import org.reactome.server.diagram.converter.layout.output.Node;
import org.reactome.server.graph.exception.CustomQueryException;
import org.reactome.server.graph.service.AdvancedDatabaseObjectService;
import org.reactome.server.graph.utils.ReactomeGraphCore;

import java.util.*;

/**
 * For a given list of nodes contained in a diagram, this class produces a graph with
 * the underlying physical entities and their children. This information will be sent
 * to the client in a second batch so the graph can be kept
 *
 * @author Antonio Fabregat <fabregat@ebi.ac.uk>
 */
public class DiagramGraphFactory {

    private static final AdvancedDatabaseObjectService advancedDatabaseObjectService = ReactomeGraphCore.getService(AdvancedDatabaseObjectService.class);

    public Graph getGraph(Diagram diagram) {
        return new Graph(diagram.getDbId(),
                diagram.getStableId(),
                diagram.getDisplayName(),
                getGraphNodes(diagram),
                getGraphEdges(diagram),
                getSubpathways(diagram));
    }

    //The buffer is used in building time to avoid querying/decomposition of those previously processed
    private Map<Long, EntityNode> entityNodeMap = new HashMap<>();

    public Map<Long, EntityNode> getEntityNodeMap() {
        return entityNodeMap;
    }

    private Set<EntityNode> getGraphNodes(Diagram diagram) {
        Set<EntityNode> rtn = new HashSet<>();

        List<Long> greenboxes = new ArrayList<>();
        for (Node node : diagram.getNodes()) {
            if(node.renderableClass.equals("ProcessNode")){
                greenboxes.add(node.reactomeId);
            }
        }

        Map<String, Object> parametersMap = new HashMap<>();
        parametersMap.put("list", greenboxes);
        String query = "" +
                "MATCH (p:Pathway)-[:species]->(s:Species) " +
                "WHERE p.dbId IN {list} " +
                "RETURN DISTINCT p.dbId AS dbId, " +
                "                p.stId AS stId, " +
                "                p.displayName AS displayName, " +
                "                p.schemaClass AS schemaClass, " +
                "                s.dbId AS speciesID";
        try {
            Collection<QueryResult> nodesQueryResults = advancedDatabaseObjectService.customQueryForObjects(QueryResult.class, query, parametersMap);
            for (QueryResult result : nodesQueryResults) {
                rtn.add(new EntityNode(result, diagram.getDiagramIds(result.getDbId())));
            }
        } catch (CustomQueryException e) {
            e.printStackTrace();
        }

        query = "" +
                "MATCH path=(p:Pathway{dbId:{dbId}})-[:hasEvent*]->(rle:ReactionLikeEvent) " +
                "WHERE SINGLE(x IN NODES(path) WHERE (x:Pathway) AND x.hasDiagram) " +
                "MATCH (rle)-[:input|output|catalystActivity|entityFunctionalStatus|physicalEntity|regulatedBy|regulator|hasComponent|hasMember|hasCandidate|repeatedUnit*]->(pe:PhysicalEntity) " +
                "WITH DISTINCT pe " +
                "OPTIONAL MATCH (pe)-[:hasComponent|hasMember|hasCandidate|repeatedUnit]->(children:PhysicalEntity) " +
                "OPTIONAL MATCH (parent:PhysicalEntity)-[:hasComponent|hasMember|hasCandidate|repeatedUnit]->(pe) " +
                "OPTIONAL MATCH (pe)-[:referenceEntity]->(re:ReferenceEntity) " +
                "OPTIONAL MATCH (pe)-[:species]->(s:Species) " +
                "RETURN pe.dbId AS dbId, pe.stId AS stId, pe.displayName AS displayName, pe.schemaClass AS schemaClass, " +
                "       s.dbId AS speciesID, " +
                "       COLLECT(DISTINCT children.dbId) AS children, " +
                "       COLLECT(DISTINCT parent.dbId) AS parents, " +
                "       CASE WHEN re.variantIdentifier IS NULL THEN re.identifier ELSE re.variantIdentifier END AS identifier, " +
                "       re.geneName AS geneNames";

        parametersMap.put("dbId", diagram.getDbId());
        try {
            Collection<NodesQueryResult> nodesQueryResults = advancedDatabaseObjectService.customQueryForObjects(NodesQueryResult.class, query, parametersMap);
            for (NodesQueryResult result : nodesQueryResults) {
                rtn.add(new EntityNode(result, diagram.getDiagramIds(result.getDbId())));
            }
        } catch (CustomQueryException e) {
            e.printStackTrace();
        }
        return rtn.isEmpty() ? null : rtn;
    }

    private Collection<EventNode> getGraphEdges(Diagram diagram) {
        List<EventNode> rtn = new ArrayList<>();
        String query = "" +
                "MATCH path=(p:Pathway{dbId:{dbId}})-[:hasEvent*]->(:ReactionLikeEvent) " +
                "WHERE SINGLE(x IN NODES(path) WHERE (x:Pathway) AND x.hasDiagram) " +
                "WITH DISTINCT p, LAST(NODES(path)) AS rle " +
                "OPTIONAL MATCH (rle)-[:input]->(i:PhysicalEntity) " +
                "OPTIONAL MATCH (rle)-[:output]->(o:PhysicalEntity) " +
                "OPTIONAL MATCH (rle)-[:catalystActivity|entityFunctionalStatus|physicalEntity*]->(c:PhysicalEntity) " +
                "OPTIONAL MATCH (rle)-[:regulatedBy]->(reg:Regulation)-[:regulator]->(r:PhysicalEntity) " +
                "OPTIONAL MATCH (pre:ReactionLikeEvent)<-[:precedingEvent]-(rle) " +
                "OPTIONAL MATCH (fol:ReactionLikeEvent)-[:precedingEvent]->(rle) " +
                "RETURN  rle.dbId AS dbId, rle.stId as stId, rle.displayName AS displayName, rle.schemaClass AS schemaClass, " +
                "        COLLECT(DISTINCT i.dbId) AS inputs, " +
                "        COLLECT(DISTINCT o.dbId) AS outputs, " +
                "        COLLECT(DISTINCT c.dbId) AS catalysts, " +
                "        CASE WHEN reg IS NULL THEN [] ELSE COLLECT(DISTINCT {type: reg.schemaClass, dbId: r.dbId}) END AS regulation, " +
                "        COLLECT(DISTINCT pre.dbId) AS preceding, " +
                "        COLLECT(DISTINCT fol.dbId) AS following";
        Map<String, Object> parametersMap = new HashMap<>();
        parametersMap.put("dbId", diagram.getDbId());
        try {
            Collection<EdgesQueryResult> edgesQueryResults = advancedDatabaseObjectService.customQueryForObjects(EdgesQueryResult.class, query, parametersMap);
            for (EdgesQueryResult result : edgesQueryResults) {
               rtn.add(new EventNode(result, diagram.getDiagramIds(result.getDbId())));
            }
        } catch (CustomQueryException e) {
            e.printStackTrace();
        }
        return rtn.isEmpty() ? null : rtn;
    }

    private Collection<SubpathwayNode> getSubpathways(Diagram diagram) {
        Set<SubpathwayNode> rtn = new HashSet<>();

        String query = "" +
                "MATCH path=(p:Pathway{dbId:{dbId}})-[:hasEvent*]->(s:Event) " +
                "WHERE NONE(x IN NODES(path) WHERE (x:ReactionLikeEvent)) AND NONE(x IN TAIL(NODES(path)) WHERE x.hasDiagram) " +
                "WITH DISTINCT s, SIZE(TAIL(NODES(path))) AS level " +
                "MATCH path=(s)-[:hasEvent*]->(rle:ReactionLikeEvent) " +
                "WHERE NONE(x IN NODES(path) WHERE (x:Pathway) AND x.hasDiagram) " +
                "RETURN DISTINCT s.dbId AS dbId," +
                "                s.stId AS stId, " +
                "                s.displayName AS displayName, " +
                "                COLLECT(rle.dbId) AS events, " +
                "                level";
        Map<String, Object> parametersMap = new HashMap<>();
        parametersMap.put("dbId", diagram.getDbId());
        try {
            Collection<SubpathwaysQueryResult> subpathwaysQueryResults = advancedDatabaseObjectService.customQueryForObjects(SubpathwaysQueryResult.class, query, parametersMap);
            for (SubpathwaysQueryResult result : subpathwaysQueryResults) {
                rtn.add(new SubpathwayNode(result));
            }
        } catch (CustomQueryException e) {
            e.printStackTrace();
        }
        return rtn.isEmpty() ? null : rtn;
    }
}