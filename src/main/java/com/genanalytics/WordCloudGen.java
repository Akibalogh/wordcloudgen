package genanalytics;

import java.io.BufferedInputStream;
import java.io.PrintStream;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import org.neo4j.kernel.EmbeddedGraphDatabase;

import org.neo4j.server.WrappingNeoServerBootstrapper;

public class WordCloudGen 
{
	private static GraphDatabaseService graphDb;	

	public GraphDatabaseService getGraphDb() { return graphDb; }

	public void setGraphDb(GraphDatabaseService graphDb) { this.graphDb = graphDb; }

	public Node returnNode(Long id) { return getGraphDb().getNodeById(id); }
	
	
	public static void main( String[] args )
    	{
		String DB_PATH = "/usr/share/neo4j-community-1.8.1/data/articles-categories.db";
		GraphDatabaseFactory neoFactory = new GraphDatabaseFactory();
		
		WordCloudGen w = new WordCloudGen();
		
		w.setGraphDb(neoFactory.newEmbeddedDatabase(DB_PATH));
		registerShutdownHook();

		for (long id = 1L; id < 100L; id++)
		{
			Node a = w.returnNode(id);

			String property = "value";
			System.out.println(a.getProperty(property));
		}
	}

	private static void registerShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook( new Thread()
		{
			@Override
			public void run()
			{
				graphDb.shutdown();
			}
		} );
	}
}

