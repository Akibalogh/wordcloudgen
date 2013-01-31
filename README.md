wordcloudgen
=============

This application generates a word cloud of like terms by utilizing article structure in DBPedia (Wikipedia). 
It enables a user to interactively query DBPedia articles and categories within a Neo4j dataset.

Source DBPedia datasets (original and cleaned) at: /data
<br>Neo4j dataset found in: /opt/neo4j/data

Please note that enough memory needs to be available to load all Nodes and Indexes.
Also you can use _JAVA_OPTS to set the heap size to improve performance on large loads.

Currently this application implements Neo4j Transactions but BatchInserter will be implemented in a future version.
