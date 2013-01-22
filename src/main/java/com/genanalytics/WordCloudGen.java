package genanalytics;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.PrintStream;
import java.io.IOException;
import java.io.Console;
import java.util.HashMap;
import java.util.Map;

//import org.neo4j.graphdb.DynamicRelationshipType;
//import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import org.neo4j.kernel.EmbeddedGraphDatabase;

import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.neo4j.unsafe.batchinsert.BatchInserterIndex;
import org.neo4j.unsafe.batchinsert.BatchInserterIndexProvider;
//import org.neo4j.server.WrappingNeoServerBootstrapper;


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
		// final String DB_PATH = "/opt/neo4j/data/articles-categories.db";
		final String DB_PATH = "/opt/neo4j/data/articles-shortened.db";
		final String OUT_FILE_PATH = "/home/akibalogh/";
		final String PREFIX = "U http://dbpedia.org/resource/";
		String keywords = "";
		String property = "value";
		String input = null;

		WordCloudGen w = databaseSetup(DB_PATH, property);
		// When the index is queried, return results
		Console console = System.console();

		do
		{
			input = console.readLine("Enter a keyword > ");
		
			if (!input.isEmpty())
			{
				keywords = checkIndex(w.getIndex(), property, PREFIX, input);
				
				if (keywords.length() > 0)
				{
					System.out.println("Writing to " + OUT_FILE_PATH + input + ".php");
					writeCurlOutput(keywords, OUT_FILE_PATH, input + ".php");
				}
			}	
		}
		while (!input.isEmpty());

		
		System.out.println("Bye!");	
		w.getGraphDb().shutdown(); // necessary w/ shutdown hook?
	
		// catch (Exception error)
		// { System.err.println("Error: " + e.getMessage()); }

	}


	public static WordCloudGen databaseSetup(String DB_PATH, String property)
	{
		GraphDatabaseFactory neoFactory = new GraphDatabaseFactory();
		
		WordCloudGen w = new WordCloudGen();
		w.setGraphDb(neoFactory.newEmbeddedDatabase(DB_PATH));
		
		registerShutdownHook();

		// Get all nodes
		Iterable<Node> result = w.getGraphDb().getAllNodes();

		// Find out the size of the list
		int listsize = 0;
		
		for (Node row : result)
		{ listsize++; }

		System.out.println("Loaded " + listsize + " entries");



		// Load index
		
		/* WORK-IN-PROGRESS: Implement BatchInserter and BatchInserterIndex
		Note: Batch insertion must be single-threaded! BatchIns has no transaction handling, locking and cache layers
		config.put("neostore.nodestore.db.mapped_memory", "100M");		
		BatchInserter inserter = BatchInserters.inserter(DB_PATH + "/batchinserter", config);
		BatchInserterIndexProvider indexProvider = new LuceneBatchInserterIndexProvider(inserter);
		Map<String, Object> properties = new HashMap<String, Object>();
		*/ 

		Transaction tx= w.getGraphDb().beginTx();
		long percentprog = 0L;

		try
		{
			IndexManager indexManager = w.getGraphDb().index();
			w.setIndex(indexManager.forNodes(property));

			System.out.println("Index set. Loading into index..");

			// For each node in the graph, add it to the index
			for (Node row : result)
			{ 
				if (row.hasProperty(property))
				{ w.getIndex().add(row, property, row.getProperty(property)); }

				// Show a progress update every 250K nodes
				if (row.getId() % 250000 == 0)
				{ 
					percentprog = (row.getId() * 100) / (listsize);
					System.out.println(row.getId() + " of " + listsize + " loaded | " + percentprog + "% loaded");
					tx.success();
				}
				
				// Commit transactions after every 250K nodes
				if (row.getId() % 250000 == 0)
				{
					tx.success();
					tx.finish();

					tx = w.getGraphDb().beginTx();
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

	public static String checkIndex (Index<Node> index, String property, String PREFIX, String input)
	{
		String relationshipValue;
		String relationshipArray = "";
		Iterable<Relationship> result = null;
		int prefixLen = PREFIX.length();
		
		try
		{
			IndexHits<Node> hits = index.get(property, PREFIX + input);
			Node results = hits.getSingle();
	
			result = results.getRelationships();

			for (Relationship relationship : result)
			{ 
				// TODO: Distinguish between Articles and Categories
				
				relationshipValue = (String)relationship.getEndNode().getProperty(property); 
		
				// If we've found a category, remove the Category designation
				if (relationshipValue.contains("Category:"))
				{ relationshipValue = relationshipValue.substring(prefixLen + "Category:".length()); }

				// Replace all _ with spaces
				if (relationshipValue.contains("_"))
				{ relationshipValue = relationshipValue.replace("_", " "); }

				// relationshipValue = relationshipValue.substring(prefixLen);
				System.out.println("Relationship found: " + relationshipValue);
				relationshipArray += "\"" + relationshipValue + "\",";
			}
			
			// Remove last comma from array
			relationshipArray = relationshipArray.substring(0, relationshipArray.length() - 1); 
		}
		catch (NullPointerException e)
		{ System.err.println(input + " not found!"); }
		
		return relationshipArray;
	}

	public static void writeCurlOutput (String keywords, String outPath, String outFileName)
	{
		try 
		{
			FileWriter fstream = new FileWriter(outPath + outFileName);
			BufferedWriter fout = new BufferedWriter(fstream);

			fout.write("<?php\n");
		
			fout.write("$data = array('keyword' => " + keywords);	
			fout.write(");\n");

			fout.write("$ch = curl_init();\n");
			fout.write("curl_setopt($ch, CURLOPT_SLL_VERIFYHOST, 0);\n");
			fout.write("curl_setopt($ch, CURLOPT_SSL_VERFIYPEER, false);\n");
			fout.write("curl_setopt($ch, CURLOPT_URL, 'http://aafter.org/news_search/index1.php?uid=123456&afid=abcdef');\n");
			fout.write("curl_setopt($ch, CURLOPT_POST, 1);\n");
			fout.write("curl_setopt($ch, CURLOPT_POSTFIELDS, $data);\n");
			fout.write("ob_start();\n");
			fout.write("curl_exec($ch);\n");
			fout.write("curl_close($ch);\n");
			fout.write("$str_xml = ob_get_contents();\n");
			fout.write("ob_end_clean();\n"); 
			fout.write("print $str_xml;\n");
			fout.write("?>");

			fout.close();
		}
		catch (Exception e)
		{ System.err.println("Error: " + e.getMessage()); }

	}
}

