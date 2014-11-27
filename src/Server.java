import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.ScoredObject;

public class Server {
	static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    static LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);

	
    public static void main(String[] args) {
    	int portNum = 9000;
    	try {
	        HttpServer server = HttpServer.create(new InetSocketAddress(portNum), 0);
	        server.createContext("/dep", new MyHandler());
	        System.out.println(portNum);
	        server.setExecutor(null); // creates a default executor
	        server.start();
    	}
    	catch (Exception e) {
    		System.out.println(e.getMessage());
    	}
    }

    static class MyHandler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
        	try {
	        	String response = "";
	        	String queryString = t.getRequestURI().getQuery();
	
	        	// Do not run mapping if client did not send any data.
	        	if (queryString != null) {
		        	Map <String, String> params = queryToMap(queryString);
		        	String paragraph = params.get("paragraph");
		        	String [] mywords = paragraph.split("\\+");
		        	for (String word: mywords) System.out.println(word);
		        	paragraph = paragraph.replaceAll("\\+", " ");
		        	System.out.println("paragraph: " + paragraph);
		        	int k = Integer.parseInt(params.get("k"));
		        	System.out.println("k: " + k);
		        	

		        	Dep [] deps = wordsToBestKDeps(mywords, k);
		        	response = Arrays.toString(deps);
		        	/*
		        	ArrayList <String []> sentenceList = stringToSentenceList(paragraph);	        	
		        	ArrayList <String> ssList = new ArrayList<String>();
		        	
		        	for (String [] words : sentenceList) {
			        	Dep [] deps = wordsToBestKDeps(words, k);
			        	String local = "{";
			        	local += "\"deps\": " + Arrays.toString(deps) + ", ";
			        	local += "\"words\": " + Arrays.toString(attachQ(words)) + "}";
			        	ssList.add(local);
		        	}
		        	response += ssList.toString();
		        	*/
	        	}
	        	else {
	        		response = "error";
	        	}
	        	
	        	// Send back the response
	            t.sendResponseHeaders(200, response.length());
	            OutputStream os = t.getResponseBody();
	            os.write(response.getBytes());
	            os.close();
        	}
        	catch (Exception e) {
        		e.printStackTrace();
        	}
        }
        
        public String [] attachQ(String [] words) {
        	String [] out = new String [words.length];
        	for (int i = 0; i < words.length; i ++) {
        		out[i] = "\"" + words[i] + "\"";
        	}
        	return out;
        }
        
        public ArrayList<String []> stringToSentenceList(String paragraph) {
        	Reader reader = new StringReader(paragraph);
        	DocumentPreprocessor dp = new DocumentPreprocessor(reader);
        	ArrayList<String []> sentenceList = new ArrayList<String []>();
        	Iterator <List<HasWord>> it = dp.iterator();
        	while (it.hasNext()) {
        		List<HasWord> l = it.next();
        		String [] words = new String[l.size()];
        		for (int i = 0; i < l.size(); i ++) {
        			words[i] = l.get(i).toString();
        		}
        		sentenceList.add(words);
        	}
        	return sentenceList;
        }
        
        public Dep [] wordsToBestKDeps(String [] words, int k) {
        	Dep [] deps = new Dep [k];
        	
        	List <CoreLabel> rawWords = Sentence.toCoreLabelList(words);
        	LexicalizedParserQuery lpq = (LexicalizedParserQuery) lp.parserQuery();
        	lpq.parse(rawWords);
            List<ScoredObject<Tree>> kBest = lpq.getKBestPCFGParses(k);
            
            for (int i = 0; i < k; i ++) {
            	ScoredObject<Tree> so = kBest.get(i);
            	double score = so.score();
            	Tree parse = so.object();
                TreebankLanguagePack tlp = lp.treebankLanguagePack();
                GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
                GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
                Collection<TypedDependency> tdl = gs.typedDependencies();
                String [][] tuples = new String [tdl.size()][3];

                Iterator<TypedDependency>iter = tdl.iterator();
                int j = 0;
                while (iter.hasNext()) {
                	TypedDependency td = iter.next();
                	tuples[j][0] = "\"" + td.reln().toString() + "\"";
                	tuples[j][1] = ""+td.gov().index();
                	tuples[j][2] = ""+td.dep().index();
                	j++;
                }

                /*
                List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
                String [][] tuples = new String [tdl.size()][3];
                for (int j = 0; j < tdl.size(); j ++) {
                	TypedDependency td = tdl.(j);
                	tuples[j][0] = "\"" + td.reln().toString() + "\"";
                	tuples[j][1] = ""+td.gov().index();
                	tuples[j][2] = ""+td.dep().index();
                }
                */
                deps[i] = new Dep(tuples, score);
            }
        	return deps;
        }

        /*
         * Example:
         * Map<String, String> params = queryToMap(httpExchange.getRequestURI().getQuery()); 
		 * System.out.println("param A=" + params.get("A"));
         */
        public Map<String, String> queryToMap(String query){
            Map<String, String> result = new HashMap<String, String>();
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                if (pair.length>1) {
                    result.put(pair[0], pair[1]);
                }else{
                    result.put(pair[0], "");
                }
            }
            return result;
        }

    
    }
   
}

/*
 * Object storing dependency information for a single parse tree on single sentence.
 */
class Dep {
	public double score;
	public String [][] tuples;
	
	public Dep(String [][] tuples, double score) {
		this.tuples = tuples;
		this.score = score;
	}
	
	public String toString() {
		String out = "{\"score\": " + score + 
				", \"tuples\": " + Arrays.deepToString(tuples) + "}";
		return out;
				
	}
}