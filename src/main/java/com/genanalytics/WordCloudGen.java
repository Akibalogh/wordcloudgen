package genanalytics;

import java.io.BufferedInputStream;
import java.io.PrintStream;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import org.neo4j.kernel.EmbeddedGraphDatabase;

import org.neo4j.server.WrappingNeoServerBootstrapper;

public class WordCloudGen 
{
	private static final String DB_PATH = "/usr/share/neo4j-community-1.8.1/data/articles-categories.db";
	private static GraphDatabaseService graphDb = new EmbeddedGraphDatabase(DB_PATH);
	
	// private static WrappingNeoServerBootstrapper srv
	// srv = new WrappingNeoServerBootstrapper(graphDb);
	// srv.start();

	public static void main( String[] args )
    	{
		registerShutdownHook();

		Transaction tx = graphDb.beginTx();
		try
		{
			Node n = graphDb.getNodeById(3);

			System.out.println(n.getId());
	
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}

	private static void registerShutdownHook()
	{
		Runtime.getRuntime().addShutdownHook( new Thread()
		{
			@Override
			public void run()
			{
				// srv.stop();
				graphDb.shutdown();
			}
		} );
	}
}

