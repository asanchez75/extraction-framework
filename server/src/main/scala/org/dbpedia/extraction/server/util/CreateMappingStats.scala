package org.dbpedia.extraction.server.util

import java.util.logging.Logger
import io.Source
import java.lang.IllegalArgumentException
import org.dbpedia.extraction.wikiparser.impl.wikipedia.Namespaces
import org.dbpedia.extraction.wikiparser._
import org.dbpedia.extraction.mappings._
import org.dbpedia.extraction.util.{WikiUtil,Language,Finder}
import org.dbpedia.extraction.util.RichFile.toRichFile
import scala.Serializable
import java.io._
import org.dbpedia.extraction.server.Server
import org.dbpedia.extraction.ontology.OntologyNamespaces
import org.dbpedia.extraction.destinations.{DBpediaDatasets,Dataset}
import org.dbpedia.extraction.server.util.CreateMappingStats._
import java.net.{URLDecoder, URLEncoder}
import org.dbpedia.extraction.util.StringUtils.prettyMillis

/**
 * Script to gather statistics about mappings: how often they are used, which properties are used and for what mappings exist.
 * 
 * Needs the following files (where xx is the language code):
 * 
 * infobox_properties_xx.nt
 * infobox_property_definitions_xx.nt
 * infobox_test_xx.nt
 * redirects_xx.nt
 * template_parameters_xx.nt
 *
 * Which  are generated by the following extractors:
 * 
 * org.dbpedia.extraction.mappings.RedirectExtractor
 * org.dbpedia.extraction.mappings.InfoboxExtractor
 * org.dbpedia.extraction.mappings.TemplateParameterExtractor
 * 
 * TODO: The extraction framework should be flexible and configurable enough that
 * it can write simpler formats besides N-Triples. This class would be MUCH simpler and faster
 * if it had to read simple text files without N-Triples and URI-encoding.
 */
object CreateMappingStats
{
    val logger = Logger.getLogger(getClass.getName)
    
    def main(args: Array[String])
    {
        require (args != null && args.length >= 2, "need at least two args: input dir and output dir. may be followed by list of language codes.")
        
        val inputDir = new File(args(0))
        
        val statsDir = new File(args(1))
        
        // Use all remaining args as language codes or comma or whitespace separated lists of codes
        var languages = for(arg <- args.slice(2, args.length); lang <- arg.split("[,\\s]"); if (lang.nonEmpty)) yield Language(lang)
          
        require (languages nonEmpty, "need languages for which to generate statistics files") 
        
        for (language <- languages) {
          
            val millis = System.currentTimeMillis()
            
            logger.info("creating statistics for "+language.wikiCode)
            
            val finder = new Finder[File](inputDir, language)
            
            // Note: org.dbpedia.extraction.dump.download.Download.Complete = "download-complete"
            // TODO: move that constant to core, or use config value
            val date = finder.dates("download-complete").last
            
            def inputFile(dataset: Dataset): File = {
              finder.file(date, dataset.name.replace('_','-')+".nt")
            }
            
            // extracted by org.dbpedia.extraction.mappings.RedirectExtractor
            val redirects = inputFile(DBpediaDatasets.Redirects)
            // extracted by org.dbpedia.extraction.mappings.InfoboxExtractor
            val infoboxProperties = inputFile(DBpediaDatasets.Infoboxes)
            // extracted by org.dbpedia.extraction.mappings.TemplateParameterExtractor
            val templateParameters = inputFile(DBpediaDatasets.TemplateVariables)
            // extracted by org.dbpedia.extraction.mappings.InfoboxExtractor
            val infoboxTest = inputFile(DBpediaDatasets.InfoboxTest)
            
            val builder = new MappingStatsBuilder(statsDir, language)
    
            builder.buildStats(redirects, infoboxProperties, templateParameters, infoboxTest)
            
            // load them right back to check that the format is ok
            new MappingStatsManager(statsDir, language)
            
            logger.info("created statistics for "+language.wikiCode+" in "+prettyMillis(System.currentTimeMillis - millis))
        }
    }
}
