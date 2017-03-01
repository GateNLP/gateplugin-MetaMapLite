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
import gate.Factory;
import gov.nih.nlm.nls.metamap.document.FreeText;
import gov.nih.nlm.nls.metamap.lite.types.Entity;
import gov.nih.nlm.nls.metamap.lite.types.Ev;
import gov.nih.nlm.nls.ner.MetaMapLite;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
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
    
    defProperties.setProperty("opennlp.models.directory", "public_mm_lite/data/models");
    MetaMapLite.expandModelsDir(defProperties);
    defProperties.setProperty("metamaplite.index.directory",  "public_mm_lite/data/ivf/strict");
    defProperties.setProperty("metamaplite.excluded.termsfile", "public_mm_lite/data/specialterms.txt");
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
    String docContent = document.getContent().toString();
    BioCDocument bcdocument = FreeText.instantiateBioCDocument(docContent);
        
    List<Entity> entityList = null;
    try {
      entityList = metaMapLiteInst.processDocument(bcdocument);
    } catch (Exception e){
      java.util.logging.Logger.getLogger(MetaMapLitePR.class.getName()).log(Level.SEVERE, null, e);
    }
    for (Entity entity: entityList) {
      Long start = (new Integer(entity.getOffset())).longValue();
      Long end = (new Integer(entity.getOffset() + entity.getLength())).longValue();
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
  
  
  protected String outputASName = "";
  @RunTime
  @Optional
  @CreoleParameter(
          comment = "Output annotation set, default is MetaMapLite",
          defaultValue = "MetaMapLite")
  public void setOutputAnnotationSet(String oas) {
    outputASName = oas;
  }

  public String getOutputAnnotationSet() {
    return outputASName;
  }
  
  protected String outputType = "";
  @RunTime
  @Optional
  @CreoleParameter(
          comment = "The output annotation type, default is 'Mention'",
          defaultValue = "Mention")
  public void setOutputAnnotationType(String val) {
    this.outputType = val;
  }

  public String getOutputAnnotationType() {
    return outputType;
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
  
  public enum DisambiguationMethod{NONE, FIRST;}
  protected DisambiguationMethod disamb = DisambiguationMethod.NONE;
  @RunTime
  @CreoleParameter(comment = "MetaMapLite provides no disambiguation. Rudimentary disambiguation options are available here.",
          defaultValue = "NONE")
  public void setDisamb(DisambiguationMethod dis) {
    disamb = dis;
  }
  public DisambiguationMethod getDisamb() {
    return disamb;
  }
  
}
