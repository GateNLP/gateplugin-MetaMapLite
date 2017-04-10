/**
*   Wraps MetaMapLite in a GATE PR.
*
*   G. Gorrell, February 2017.
*/

package gate.plugin.metamaplite;

import java.util.Properties;
import java.util.List;
import java.net.URL;

import org.apache.log4j.Logger;

import gate.Resource;
import gate.creole.ResourceInstantiationException;
import gate.creole.ExecutionException;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.FeatureMap;

import bioc.BioCDocument;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.Gate;
import gate.creole.ResourceData;
import gov.nih.nlm.nls.metamap.document.FreeText;
import gov.nih.nlm.nls.metamap.lite.types.Entity;
import gov.nih.nlm.nls.metamap.lite.types.Ev;
import gov.nih.nlm.nls.ner.MetaMapLite;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * A GATE PR which annotates documents using MetaMapLite
 */
@CreoleResource(name = "MetaMapLitePR", comment = "A GATE PR which annotates documents using MetaMapLite", helpURL = "http://gate.ac.uk/")
public class MetaMapLitePR extends AbstractLanguageAnalyser {
  
  public static final long serialVersionUID = 1L;

  private transient Logger logger = Logger.getLogger(MetaMapLitePR.class.getCanonicalName());
  
  MetaMapLite metaMapLiteInst = null;
      
  public Resource init() throws ResourceInstantiationException {
    Properties defProperties = MetaMapLite.getDefaultConfiguration();
    ResourceData myResourceData = Gate.getCreoleRegister().get(this.getClass().getName());
    URL creoleXml = myResourceData.getXmlFileUrl();
    File prDirectory = gate.util.Files.fileFromURL(creoleXml).getParentFile();
    
    defProperties.setProperty("opennlp.models.directory", prDirectory.getAbsolutePath() + "/public_mm_lite/data/models");
    MetaMapLite.expandModelsDir(defProperties);
    defProperties.setProperty("metamaplite.index.directory", prDirectory.getAbsolutePath() + "/public_mm_lite/data/ivf/strict");
    defProperties.setProperty("metamaplite.excluded.termsfile", prDirectory.getAbsolutePath() + "/public_mm_lite/data/specialterms.txt");
    MetaMapLite.expandIndexDir(defProperties);
  
    if(confUrl!=null){
      Properties myProperties = new Properties();
      try {
        InputStream input = confUrl.openStream();
        myProperties.load(input);
      } catch (IOException e) {
        java.util.logging.Logger.getLogger(MetaMapLitePR.class.getName()).log(Level.SEVERE, null, e);
      }
      for(Object key : myProperties.keySet()){
        defProperties.setProperty(key.toString(), myProperties.getProperty(key.toString()));
      }
    }
        
    try{
      metaMapLiteInst = new MetaMapLite(defProperties);
    } catch(IOException | ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException e){
      java.util.logging.Logger.getLogger(MetaMapLitePR.class.getName()).log(Level.SEVERE, null, e);
    }
    return this;
  }

  @Override
  public void execute() throws ExecutionException {
    //We're going to split the document into sentences ourselves because
    //the more text we give to MetaMapLite the more likely it is that
    //the offsets will come back wrong.
    AnnotationSet sentences = document.getAnnotations(sentenceASName).get(sentenceType);
    if(!(sentences.size()>0)) System.out.println("MetaMapLite PR failed to find any sentences to annotate!");
    for(Annotation sentence : sentences){
      long sentenceoffset = sentence.getStartNode().getOffset();
      String docContent = gate.Utils.stringFor(document, sentence);
      
      //String docContent = document.getContent().toString();
      BioCDocument bcdocument = FreeText.instantiateBioCDocument(docContent);
      List<Entity> entityList = null;
      try {
        entityList = metaMapLiteInst.processDocument(bcdocument);
      } catch (Exception e){
        java.util.logging.Logger.getLogger(MetaMapLitePR.class.getName()).log(Level.SEVERE, null, e);
      }
      for (Entity entity: entityList) {
        Long gateoffset = (new Integer(entity.getOffset())).longValue();
        Long start = sentenceoffset + gateoffset;
        Long end = start + (new Integer(entity.getLength())).longValue();
        for (Ev ev: entity.getEvSet()) {
          FeatureMap fm = Factory.newFeatureMap();
          fm.put("CUI", ev.getConceptInfo().getCUI());
          fm.put("score", ev.getScore());
          fm.put("PREF", ev.getConceptInfo().getPreferredName());
          fm.put("STYS", ev.getConceptInfo().getSemanticTypeSet());
          fm.put("VOCABS", ev.getConceptInfo().getSourceSet());

          try {
            document.getAnnotations(outputASName).add(start, end, outputType, fm);
            if(disamb==DisambiguationMethod.FIRST) break;
          } catch(Exception e){
            java.util.logging.Logger.getLogger(MetaMapLitePR.class.getName()).log(Level.SEVERE, null, e);
          }
        }
      }
    }
  }
  
  protected String outputASName = "";
  @RunTime
  @CreoleParameter(
          comment = "Output annotation set, default is MetaMapLite",
          defaultValue = "MetaMapLite")
  public void setOutputASName(String oas) {
    outputASName = oas;
  }

  public String getOutputASName() {
    return outputASName;
  }
  
  protected String outputType = "";
  @RunTime
  @CreoleParameter(
          comment = "The output annotation type, default is 'Mention'",
          defaultValue = "Mention")
  public void setOutputType(String val) {
    this.outputType = val;
  }

  public String getOutputType() {
    return outputType;
  }
  
  protected String sentenceASName = "";
  @RunTime
  @CreoleParameter(
          comment = "There need to be sentences. What annotation set are they in?",
          defaultValue = "")
  public void setSentenceASName(String sas) {
    sentenceASName = sas;
  }

  public String getSentenceASName() {
    return sentenceASName;
  }
  
  protected String sentenceType = "";
  @RunTime
  @CreoleParameter(
          comment = "The type of the annotation to use as sentence.",
          defaultValue = "Sentence")
  public void setSentenceType(String sat) {
    this.sentenceType = sat;
  }

  public String getSentenceType() {
    return sentenceType;
  }
  
  protected URL confUrl = null;
  @Optional
  @CreoleParameter(comment = "Location of an optional MetaMapLite configuration file.")
  public void setConfUrl(URL url) {
    confUrl = url;
  }
  public URL getConfUrl() {
    return confUrl;
  }
  
  public enum DisambiguationMethod{ALL, FIRST;}
  protected DisambiguationMethod disamb = DisambiguationMethod.ALL;
  @RunTime
  @CreoleParameter(comment = "MetaMapLite provides no disambiguation. Rudimentary options are available here.",
          defaultValue = "ALL")
  public void setDisamb(DisambiguationMethod dis) {
    disamb = dis;
  }
  public DisambiguationMethod getDisamb() {
    return disamb;
  }
  
}
