package genanalytics;

import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.PrintStream;
import java.io.IOException;
import java.io.Console;
import java.util.HashMap;
import java.util.Map;

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

	public static void main( String[] args )
    	{ 
		final String DB_PATH = "/opt/neo4j/data/articles-categories.db"; // Full dataset
		// final String DB_PATH = "/opt/neo4j/data/articles-shortened.db"; // Sample dataset for Development and Test
		
		final String OUT_FILE_PATH = "/var/www/html/search-scripts/";
		final String PREFIX = "U http://dbpedia.org/resource/";
		String keywords = "";
		String property = "value";
		String input = null;

		WordCloudGen w = databaseSetup(DB_PATH, property);
		// When the index is queried, return results
		Console console = System.console();

		do
		{
			input = console.readLine("Enter a keyword or press ENTER to exit > ");
		
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
	}


	public static WordCloudGen databaseSetup(String DB_PATH, String property)
	{
		GraphDatabaseFactory neoFactory = new GraphDatabaseFactory();
		
		WordCloudGen w = new WordCloudGen();
		w.setGraphDb(neoFactory.newEmbeddedDatabase(DB_PATH));
		
		registerShutdownHook();

		// Load all nodes
		System.out.println("Loading Nodes into memory..");
		Iterable<Node> result = w.getGraphDb().getAllNodes();

		// Find out the size of the list
		int listsize = 0;
		
		for (Node row : result)
		{ listsize++; }

		System.out.println("Loaded " + listsize + " Nodes");
	
		// Check for existing index
		if (w.getGraphDb().index().existsForNodes(property))
		{
			// Index already exists
			System.out.println("Index already exists. No need to load");
			w.setIndex(w.getGraphDb().index().forNodes(property));
			return w;
		}
		else
		{
			// Index has to be created	
			IndexManager indexManager = w.getGraphDb().index();
	
			/* TODO: Implement BatchInserter and BatchInserterIndex
			Note: Batch insertion must be single-threaded! BatchIns has no transaction handling, locking and cache layers
			config.put("neostore.nodestore.db.mapped_memory", "100M");		
			BatchInserter inserter = BatchInserters.inserter(DB_PATH + "/batchinserter", config);
			BatchInserterIndexProvider indexProvider = new LuceneBatchInserterIndexProvider(inserter);
			Map<String, Object> properties = new HashMap<String, Object>();
			*/ 

			Transaction tx= w.getGraphDb().beginTx();
			long percentProg = 0L;
			long heapUsed = 0L;
			double heapUtil;

			try
			{
				w.setIndex(indexManager.forNodes(property));

				System.out.println("Index does not exist. Loading into index..");

				// For each node in the graph, add it to the index
				for (Node row : result)
				{ 
					if (row.hasProperty(property))
					{ w.getIndex().add(row, property, row.getProperty(property)); }

					// Check heap utilization every 5K nodes
					if (row.getId() % 5000 == 0)
					{ 
						heapUtil = 1 - ((double)Runtime.getRuntime().freeMemory() / (double)Runtime.getRuntime().totalMemory());

						if (heapUtil > 0.90)
						{ System.out.println("WARNING! High heap utilization at " + row.getId() + " | Utiliz: " + (int)(heapUtil * 100) + "% | Total: " + Runtime.getRuntime().totalMemory() + " | Free: " + Runtime.getRuntime().freeMemory()); }
					}

					// Show a progress update every 250K nodes
					if (row.getId() % 250000 == 0)
					{ 
						percentProg = (row.getId() * 100) / (listsize);
						System.out.println(row.getId() + " of " + listsize + " loaded | " + percentProg + "% loaded");
						tx.success();
					}
				
					// Commit transactions after every 100K nodes
					if (row.getId() % 100000 == 0)
					{
						tx.success();
						tx.finish();

						tx = w.getGraphDb().beginTx();
					}
				}
		
				tx.success();
			}
			
			finally
			{ tx.finish(); }

			System.out.println("Index loaded");
			return w;
		}
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
	
		// TODO: Implement more robust inputs for Subhankar's API v2

		// System.out.println("Assuming 15 results per keyword.");
		// System.out.println("Assuming seed URL is http://en.wikipedia.com/wiki/<keyword>");
	
		try
		{
			// First, search for Term in index
			IndexHits<Node> term = index.get(property, PREFIX + input);
			Node termResults = term.getSingle();
			Iterable<Relationship> termResult = termResults.getRelationships();

			for (Relationship relationship : termResult)
			{ 
				relationshipValue = (String)relationship.getEndNode().getProperty(property); 
				
				relationshipValue = cleanRelationship(relationshipValue, PREFIX.length());
				System.out.println("Relationship found: " + relationshipValue);

				relationshipArray += "\"" + relationshipValue + "\",";
			}

			// Next, search for Category:Term and pull any additional values in
			IndexHits<Node> category = index.get(property, PREFIX + "Category:" + input);
			Node categoryResults = category.getSingle();
			Iterable<Relationship> categoryResult = categoryResults.getRelationships();
		
			for (Relationship relationship : categoryResult)
			{ 
				relationshipValue = (String)relationship.getStartNode().getProperty(property); 
				
				relationshipValue = cleanRelationship(relationshipValue, PREFIX.length());
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


			// Subhankar's API v1
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

	public static String cleanRelationship (String relationshipValue, int prefixLen)
	{
		// Remove the prefix and Category designation, if exists
		if (relationshipValue.contains("Category:"))
		{ relationshipValue = relationshipValue.substring(prefixLen + "Category:".length()); }
		else
		{ relationshipValue = relationshipValue.substring(prefixLen); }

		// Replace all _ with spaces
		if (relationshipValue.contains("_"))
		{ relationshipValue = relationshipValue.replace("_", " "); }

		return relationshipValue;
	}
}

