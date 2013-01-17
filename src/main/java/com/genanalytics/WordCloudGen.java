package genanalytics;

import java.io.PrintStream;
import java.io.IOException;
import java.io.Console;

import java.text.DecimalFormat;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import org.neo4j.kernel.EmbeddedGraphDatabase;

import org.neo4j.server.WrappingNeoServerBootstrapper;


public class WordCloudGen 
{
	private static GraphDatabaseService graphDb;	
	private static Index<Node> index;

	public GraphDatabaseService getGraphDb() { return graphDb; }
	public Index<Node> getIndex() { return index; }

	public void setGraphDb(GraphDatabaseService graphDb) { this.graphDb = graphDb; }
	public void setIndex(Index<Node> index) { this.index = index; }

	// public Node returnNode(Long id) { return getGraphDb().getNodeById(id); }
	// public void setNode(Node node, String key, Object property) { node.setProperty(key, property); }
	
	public static void main( String[] args )
    	{ 
		final String DB_PATH = "/usr/share/neo4j-community-1.8.1/data/articles-categories.db";
		final String PREFIX = "U http://dbpedia.org/resource/";
		String property = "value";
		String input = null;

		WordCloudGen w = databaseSetup(DB_PATH, property);
		// When the index is queried, return results
		Console console = System.console();
	
		while (input != "")
		{
			input = console.readLine("> ");
			System.out.println("I read: '" + PREFIX + input + "'");	
			// checkIndex(w.getIndex(), property, input);	
		}
		System.out.println("Bye!");	
	
		//catch(IOException error)
		//{ error.printStackTrace(); }

	}


	public static WordCloudGen databaseSetup(String DB_PATH, String property)
	{
		GraphDatabaseFactory neoFactory = new GraphDatabaseFactory();
		
		WordCloudGen w = new WordCloudGen();
		w.setGraphDb(neoFactory.newEmbeddedDatabase(DB_PATH));
		
		registerShutdownHook();

		Iterable<Node> result = w.getGraphDb().getAllNodes();

		// Find out the size of the list
		int listsize = 0;
		
		for (Node row : result)
		{ listsize++; }

		System.out.println("Loaded " + listsize + " entries");

		Transaction tx = w.getGraphDb().beginTx();

		long percentprog = 0L;

		try
		{
			IndexManager indexManager = w.getGraphDb().index();
			w.setIndex(indexManager.forNodes(property));

			System.out.println("Index set. Loading into index..");

			// For each node in the graph, add it to the index
			// TODO: Consider using Batch Index load
			for (Node row : result)
			{ 
				if (row.hasProperty(property))
				{ w.getIndex().add(row, property, row.getProperty(property)); }

				if (row.getId() % 40000 == 0)
				{ 
					percentprog = (row.getId() * 100) / (listsize);
					System.out.println(row.getId() + " of " + listsize + " loaded | " + percentprog + "% loaded");
					tx.success();
				}

				// TODO: Probably need to add relationships as well
			}
		tx.success();
		}
		finally
		{ tx.finish(); }

		System.out.println("Index loaded");
		return w;
	}

	private static void registerShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook( new Thread()
		{
			@Override
			public void run()
			{ graphDb.shutdown(); }
		} );
	}

	public static void checkIndex (Index<Node> index, String property, String input)
	{
		IndexHits<Node> hits = index.get(property, input);
		Node results = hits.getSingle();

		System.out.println(results.getProperty(property));
	}
}

