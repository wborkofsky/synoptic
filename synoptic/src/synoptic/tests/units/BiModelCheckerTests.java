package synoptic.tests.units;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import synoptic.invariants.AlwaysFollowedInvariant;
import synoptic.invariants.AlwaysPrecedesInvariant;
import synoptic.invariants.CExamplePath;
import synoptic.invariants.ITemporalInvariant;
import synoptic.invariants.NeverFollowedInvariant;
import synoptic.invariants.TemporalInvariantSet;
import synoptic.invariants.miners.TransitiveClosureInvMiner;
import synoptic.main.parser.ParseException;
import synoptic.main.parser.TraceParser;
import synoptic.model.ChainsTraceGraph;
import synoptic.model.EventNode;
import synoptic.model.Partition;
import synoptic.model.PartitionGraph;
import synoptic.model.event.EventType;
import synoptic.model.interfaces.IGraph;
import synoptic.model.interfaces.INode;
import synoptic.model.interfaces.ITransition;
import synoptic.tests.SynopticTest;
import synoptic.util.InternalSynopticException;

/**
 * Checks the FSM model checker against the NASA model checker to compare their
 * results for generating counter examples of temporal invariants on graphs.
 * This is a parameterized JUnit test -- tests in this class are run with
 * parameters generated by method annotated with @Parameters.
 * 
 * @author ivan
 */
@RunWith(value = Parameterized.class)
public class BiModelCheckerTests extends SynopticTest {
    
    public static String nonTimeRelation = "r";
    
    public static final ITemporalInvariant aAFbyB = 
            new AlwaysFollowedInvariant("a", "b", nonTimeRelation);
    
    public static final ITemporalInvariant aNFbyB = 
            new NeverFollowedInvariant("a", "b", nonTimeRelation);
    
    public static final ITemporalInvariant aAPb = 
            new AlwaysPrecedesInvariant("a", "b", nonTimeRelation);

    /**
     * Generates parameters for this unit test. The first instance of this test
     * (using first set of parameters) will run using the FSM checker, while the
     * second instance (using the second set of parameters) will run using the
     * NASA model checker.
     * 
     * @return The set of parameters to pass to the constructor the unit test.
     */
    @Parameters
    public static Collection<Object[]> data() {
        Object[][] data = new Object[][] { { true }};
        return Arrays.asList(data);
    }

    boolean useFSMChecker;
    

    public BiModelCheckerTests(boolean useFSMChecker) {
        this.useFSMChecker = useFSMChecker;
    }

    @Before
    public void setUp() throws ParseException {
        super.setUp();
        synoptic.main.SynopticMain.getInstanceWithExistenceCheck().options.useFSMChecker = this.useFSMChecker;
        synoptic.main.SynopticMain.getInstanceWithExistenceCheck().options.multipleRelations = true;
    }

    /**
     * Test that the graph g generates or not (depending on the value of
     * cExampleExists) a counter-example for invariant inv, which is exactly the
     * expectedPath through the graph g.
     */
    @SuppressWarnings("null")
    private static <T extends INode<T>> void testCExamplePath(IGraph<T> g,
            ITemporalInvariant inv, boolean cExampleExists, List<T> expectedPath)
            throws InternalSynopticException {

        TemporalInvariantSet invs = new TemporalInvariantSet();
        invs.add(inv);

        List<CExamplePath<T>> cexamples = invs.getAllCounterExamples(g);

        if (cexamples != null) {
            logger.info("model-checker counter-example:"
                    + cexamples.get(0).path);
        }

        if (!cExampleExists) {
            assertTrue(cexamples == null);
            return;
        }

        // Else, there should be just one counter-example
        assertTrue(cexamples != null);
        assertTrue(cexamples.size() == 1);
        List<T> cexamplePath = cexamples.get(0).path;

        // logger.info("model-checker counter-example:" + cexamplePath);
        logger.info("correct counter-example:" + expectedPath);

        // Check that the counter-example is of the right length.
        assertTrue(cexamplePath.size() == expectedPath.size());

        // Check that cexamplePath is exactly the expectedPath
        for (int i = 0; i < cexamplePath.size(); i++) {
            assertTrue(cexamplePath.get(i) == expectedPath.get(i));
        }
        return;
    }

    /**
     * Test that the list of events representing a linear graph generates or not
     * (depending on value of cExampleExists) a single counter-example for
     * invariant inv that includes the prefix of linear graph of length up to
     * cExampleIndex (which starts counting at 0 = INITIAL, and may index
     * TERMINAL).
     */
    private void testLinearGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            int lastCExampleIndex) throws InternalSynopticException,
            ParseException {
        // Create the graph.
        ChainsTraceGraph g = genInitialLinearGraph(events);

        if (!cExampleExists) {
            // Don't bother constructing the counter-example path.
            testCExamplePath(g, inv, cExampleExists, null);
            return;
        }

        // Build the expectedPath by traversing the entire graph.
        LinkedList<EventNode> expectedPath = new LinkedList<EventNode>();
        EventNode nextNode = g.getDummyInitialNode();
        expectedPath.add(nextNode);
        for (int i = 1; i <= lastCExampleIndex; i++) {
            nextNode = nextNode.getAllTransitions().get(0).getTarget();
            expectedPath.add(nextNode);
        }
        testCExamplePath(g, inv, cExampleExists, expectedPath);
    }

    /**
     * The list of partially ordered events is condensed into a partition graph
     * (the most compressed model). This graph is then checked for existence or
     * not (depending on value of cExampleExists) of a counter-example for
     * invariant inv specified by cExampleLabels. The format for each event
     * string in the events array (?<TYPE>) with "^--$" as the partitions
     * separator; the format for each element in the counter-example path is
     * (?<TYPE>). <br />
     * <br/>
     * NOTE: We get away with just TYPE for specifying the counter-example
     * because we will deal with the initial partition graph -- where there is
     * exactly one node for each event type. <br />
     * <br />
     * NOTE: INITIAL is always included, therefore cExampleLabels should not
     * include it. However, if TERMINAL is to be included, it should be
     * specified in cExampleLabels.
     * @param multipleRelations TODO
     * 
     * @throws Exception
     */
    private static void testPartitionGraphCExample(String[] events,
            ITemporalInvariant inv, boolean cExampleExists,
            List<EventType> cExampleLabels, boolean multipleRelations) throws Exception {

        TraceParser parser = new TraceParser();
        
        if (multipleRelations) {
            parser.addRegex("^(?<TIME>)(?<TYPE>)$");
            parser.addRegex("^(?<TIME>)(?<RELATION>)(?<TYPE>)$");
            parser.addRegex("^(?<TIME>)(?<RELATION*>)cl(?<TYPE>)$");
            parser.addPartitionsSeparator("^--$");
        } else {
            parser.addRegex("^(?<TYPE>)$");
            parser.addPartitionsSeparator("^--$");
        }
        
        
        PartitionGraph pGraph = genInitialPartitionGraph(events, parser,
                new TransitiveClosureInvMiner(), false);

        exportTestGraph(pGraph, 1);

        if (!cExampleExists) {
            // If there no cExample then there's no reason to build a path.
            testCExamplePath(pGraph, inv, cExampleExists, null);
            return;
        }

        LinkedList<Partition> expectedPath = new LinkedList<Partition>();
        Partition nextNode = pGraph.getDummyInitialNode();

        // Build the expectedPath by traversing the graph, starting from the
        // initial node by finding the appropriate partition at each hop by
        // matching on the label of each partition.
        expectedPath.add(nextNode);
        nextCExampleHop:
        for (int i = 0; i < cExampleLabels.size(); i++) {
            EventType nextLabel = cExampleLabels.get(i);
            for (ITransition<Partition> transition : nextNode
                    .getAllTransitions()) {
                for (EventNode event : transition.getTarget().getEventNodes()) {
                    if (event.getEType().equals(nextLabel)) {
                        nextNode = transition.getTarget();
                        expectedPath.add(nextNode);
                        continue nextCExampleHop;
                    }
                }
            }
            Assert.fail("Unable to locate transition from "
                    + nextNode.toString() + " to a partition with label "
                    + nextLabel.toString());
        }
        testCExamplePath(pGraph, inv, cExampleExists, expectedPath);
    }
    
    
    @Test
    public void NoNFBiCycle() throws Exception {        
        String[] events = { "1 x", "2 r a", "3 b", "4 x" };
        testPartitionGraphCExample(events, aNFbyB, false, null, true);
    }
    
    @Test
    public void NFBiCycle() throws Exception {
        
        String[] events = { "1 x", "2 r a", "3 b", "4 r x" };
        List<EventType> cExampleLabels = 
                stringsToStringEventTypes(new String[] {"x", "a", "b"});
        testPartitionGraphCExample(events, aNFbyB, true, cExampleLabels, true);
    }
    
    @Test
    public void NFBiLinearOne() throws Exception {
        String[] events = { "1 a", "2 r a", "3 z", "4 b", "5 r b" };
        testLinearGraphCExample(events, aNFbyB, true, 4);
    }
    
    @Test
    public void NoNFBiLinearOne() throws Exception {
        
        String[] events = { "1 a", "2 b", "3 r b"};
        testLinearGraphCExample(events, aNFbyB, false, 0);
    }
    
    @Test
    public void NoNFBiLinearTwo() throws Exception {
        
        String[] events = { "1 a", "2 r a", "3 b", "4 z", "5 r z" };
        testLinearGraphCExample(events, aNFbyB, false, 0);
    }
    
    @Test
    public void NoNFBiLinearThree() throws Exception {
        
        String[] events = { "1 a", "2 r a", "3 b"};
        testLinearGraphCExample(events, aNFbyB, false, 0);
    }
    
    @Test
    public void NoAPBiCycle() throws Exception {        
        String[] events = { "1 x", "2 r a", "3 b", "4 r x" };
        testPartitionGraphCExample(events, aAPb, false, null, true);
    }
    
    @Test
    public void APBiCycle() throws Exception {        
        String[] events = { "1 x", "2 a", "3 b", "4 r x" };
        List<EventType> cExampleLabels = 
                stringsToStringEventTypes(new String[] {"b"});
        testPartitionGraphCExample(events, aAPb, true, cExampleLabels, true);
    }
    
    @Test
    public void NoAPBiLinearOne() throws InternalSynopticException, ParseException {       
        String[] events = { "1 a", "2 r a", "3 z", "4 b", "5 r b"};
        testLinearGraphCExample(events, aAPb, false, 0);
    }
    
    @Test
    public void NoAPBiLinearTwo() throws InternalSynopticException, ParseException {       
        String[] events = { "1 z", "2 r z", "3 b"};
        testLinearGraphCExample(events, aAPb, false, 0);
    }
    
    @Test
    public void APBiLinearOne() throws InternalSynopticException, ParseException {      
        String[] events = { "1 a", "2 b", "3 r b"};
        testLinearGraphCExample(events, aAPb, true, 4);
    }
    
    @Test
    public void APBiLinearTwo() throws InternalSynopticException, ParseException {        
        String[] events = { "1 z", "2 r z", "3 a", "4 b", "5 r b"};
        testLinearGraphCExample(events, aAPb, true, 6);
    }
    
    @Test
    public void NoAFBiCycle() throws Exception {        
        String[] events = { "1 x", "2 r a", "3 b", "4 r x" };
        testPartitionGraphCExample(events, aAFbyB, false, null, true);
    }
    
    @Test
    public void AFBiCycle() throws Exception {        
        String[] events = { "1 x", "2 r a", "3 b", "4 x" };
        List<EventType> cExampleLabels = 
                stringsToStringEventTypes(new String[] {"x", "a"});
        testPartitionGraphCExample(events, aAFbyB, true, cExampleLabels, true);
    }
    
    @Test
    public void NoAFBiLinearOne() throws InternalSynopticException, ParseException {        
        String[] events = { "1 a", "2 z", "3 r z"};
        testLinearGraphCExample(events, aAFbyB, false, 0);
    }
    
    @Test
    public void NoAFBiLinearTwo() throws InternalSynopticException, ParseException {       
        String[] events = { "1 a", "2 r a", "3 z", "4 b", "5 r b"};
        testLinearGraphCExample(events, aAFbyB, false, 0);
    }
    
    @Test
    public void AFBiLinearOne() throws InternalSynopticException, ParseException {     
        String[] events = { "1 a", "2 r a", "3 b", "4 z", "5 r z"};
        testLinearGraphCExample(events, aAFbyB, true, 6);
    }
    
    @Test
    public void AFBiLinearTwo() throws InternalSynopticException, ParseException {
        String[] events = { "1 a", "2 r a", "3 b"};
        testLinearGraphCExample(events, aAFbyB, true, 4);
    }

}
