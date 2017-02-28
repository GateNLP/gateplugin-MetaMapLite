/**
*   Wraps MetaMapLite in a GATE PR.
*
*   G. Gorrell, February 2017.
*/

package gate.plugin.metamaplite;

import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import gate.Resource;
import gate.creole.ResourceInstantiationException;
import gate.creole.ExecutionException;
import gate.creole.AbstractLanguageAnalyser;
import gate.Annotation;
import gate.Utils;
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

/**
 * A GATE PR which annotates documents using MetaMapLite
 */
@CreoleResource(name = "MetaMapLitePR", comment = "A GATE PR which annotates documents using MetaMapLite", helpURL = "http://gate.ac.uk/")
public class MetaMapLitePR extends AbstractLanguageAnalyser {
  
  public static final long serialVersionUID = 1L;

  private transient Logger logger = Logger.getLogger(MetaMapLitePR.class.getCanonicalName());
  
  MetaMapLite metaMapLiteInst = null;
  
  public Resource init() throws ResourceInstantiationException {
    Properties myProperties = MetaMapLite.getDefaultConfiguration();
    myProperties.setProperty("opennlp.models.directory", "public_mm_lite/data/models");
    MetaMapLite.expandModelsDir(myProperties);
    myProperties.setProperty("metamaplite.index.directory",  "public_mm_lite/data/ivf/strict");
    myProperties.setProperty("metamaplite.excluded.termsfile", "public_mm_lite/data/specialterms.txt");
    MetaMapLite.expandIndexDir(myProperties);
  
    try{
      metaMapLiteInst = new MetaMapLite(myProperties);
    } catch(Exception e){
      e.printStackTrace();
    }
    return this;
  }

  @Override
  public void execute() throws ExecutionException {
    String docContent = document.getContent().toString();
    List<BioCDocument> documentList = new ArrayList<BioCDocument>();
    BioCDocument bcdocument = FreeText.instantiateBioCDocument(docContent);
    documentList.add(bcdocument);
        
    List<Entity> entityList = null;
    try {
      entityList = metaMapLiteInst.processDocumentList(documentList);
    } catch (Exception e){
      e.printStackTrace();
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
        } catch(Exception e){
          e.printStackTrace();
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
}
