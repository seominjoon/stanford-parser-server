import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
import edu.stanford.nlp.process.DocumentProcessor;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.ScoredObject;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;


public class Server {
	static String parserModel = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
    static LexicalizedParser lp = LexicalizedParser.loadModel(parserModel);

	
    public static void main(String[] args) {
    	int portNum = 9000;
    	try {
	        HttpServer server = HttpServer.create(new InetSocketAddress(portNum), 0);
	        server.createContext("/dep", new MyDepParser());
	        server.createContext("/tok", new MyTokenizer());
	        System.out.println(portNum);
	        server.setExecutor(null); // creates a default executor
	        server.start();
    	}
    	catch (Exception e) {
    		System.out.println(e.getMessage());
    	}
    }

    
    static class MyTokenizer implements HttpHandler {
    	public void handle(HttpExchange t) throws IOException {
    		try {
    			String response = "";
    			String queryString = t.getRequestURI().getRawQuery();
    			System.out.println(queryString);
    			if (queryString != null) {
        			Map<String, String> map = queryToMap(queryString);
    				String paragraph = map.get("paragraph");
    				ArrayList<ArrayList<String>> words = paragraphToWords(paragraph);
    				System.out.println(words);
    				response = JSONValue.toJSONString(words);
    			}
    			else {
    				response = "error";
    			}
            	// Send back the response
    			
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                System.out.println(response.length());
                System.out.println(response.getBytes("UTF-8").length);
                os.write(response.getBytes("UTF-8"));
                os.close();
    		}
    		catch (Exception e) {
        		e.printStackTrace();

    		}
    	}
    	
        public ArrayList<ArrayList <String>> paragraphToWords(String paragraph) {
        	Reader reader = new StringReader(paragraph);
        	DocumentPreprocessor dp = new DocumentPreprocessor(reader);
        	ArrayList<ArrayList <String>> sentenceList = new ArrayList<ArrayList <String>>();
        	Iterator <List<HasWord>> it = dp.iterator();
        	while (it.hasNext()) {
        		List<HasWord> l = it.next();
        		ArrayList<String> words = new ArrayList<String>();
        		for (int i = 0; i < l.size(); i ++) {
        			words.add(l.get(i).toString());
        		}
        		sentenceList.add(words);
        	}
        	
        	return sentenceList;
        }

    	
        public Map<String, String> queryToMap(String query) throws IOException {
            Map<String, String> result = new HashMap<String, String>();
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                if (pair.length>1) {
                    result.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
                }else{
                    result.put(pair[0], "");
                }
            }
            return result;
        }

    }
    
    static class MyDepParser implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
        	try {
	        	String response = "";
	        	String queryString = t.getRequestURI().getRawQuery();
	
	        	// Do not run mapping if client did not send any data.
	        	if (queryString != null) {
		        	Map <String, String> params = queryToMap(queryString);
		        	String paragraph = params.get("paragraph");
		        	String [] mywords = paragraph.split(" ");
		        	for (String word: mywords) System.out.println(word);
		        	//paragraph = paragraph.replaceAll(" ", " ");
		        	System.out.println("paragraph: " + paragraph);
		        	int k = Integer.parseInt(params.get("k"));
		        	System.out.println("k: " + k);
		        	System.out.println(mywords.length);
		        	System.out.println(Arrays.toString(mywords));
		        	
		        	Dep [] deps = wordsToBestKDeps(mywords, k);
		        	response = Arrays.toString(deps);
		        	System.out.println(response);
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
                String [][] tuples = new String [tdl.size()][5];

                
                Iterator<TypedDependency>iter = tdl.iterator();
                int j = 0;
                while (iter.hasNext()) {
                	TypedDependency td = iter.next();
                	tuples[j][0] = "\"" + td.reln().toString() + "\"";
                	tuples[j][1] = ""+td.gov().index();
                	tuples[j][2] = ""+td.dep().index();
                	tuples[j][3] = "\"" + td.gov().tag() + "\"";
                	tuples[j][4] = "\"" + td.dep().tag() + "\"";
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
        public Map<String, String> queryToMap(String query) throws IOException {
            Map<String, String> result = new HashMap<String, String>();
            for (String param : query.split("&")) {
                String pair[] = param.split("=");
                if (pair.length>1) {
                    result.put(pair[0], URLDecoder.decode(pair[1], "UTF-8"));
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