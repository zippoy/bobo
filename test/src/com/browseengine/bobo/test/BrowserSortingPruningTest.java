package com.browseengine.bobo.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.Version;

import com.browseengine.bobo.api.BoboBrowser;
import com.browseengine.bobo.api.BoboIndexReader;
import com.browseengine.bobo.api.BoboIndexReaderComparator;
import com.browseengine.bobo.api.Browsable;
import com.browseengine.bobo.api.BrowseException;
import com.browseengine.bobo.api.BrowseHit;
import com.browseengine.bobo.api.BrowseRequest;
import com.browseengine.bobo.api.BrowseResult;
import com.browseengine.bobo.api.BrowseSelection;
import com.browseengine.bobo.api.FacetSpec;
import com.browseengine.bobo.api.MultiBoboBrowser;
import com.browseengine.bobo.api.Pruner;
import com.browseengine.bobo.api.FacetSpec.FacetSortSpec;
import com.browseengine.bobo.facets.FacetHandler;
import com.browseengine.bobo.facets.impl.SimpleFacetHandler;
import com.browseengine.bobo.pruner.BoboIndexReaderTimeBasedPruner;
import com.browseengine.bobo.readersort.BoboReaderTimeBasedComparator;

/**
 * Test BoboBrowser with the ability of sorting/pruning Browsables
 * 
 * @author hyan
 * @since 2.5.2
 */
public class BrowserSortingPruningTest extends TestCase
{
  static Logger log = Logger.getLogger(BrowserSortingPruningTest.class);
  private int _documentSize;
  private int _segmentSize;
  private String _idxDir = "./sortingPruningTestIndex";
  private boolean _deleteDirectory = true;

  // _reader -> _boboReaders; _subReaderList -> _boboSubReaderList
  private IndexReader _reader = null;
  private BoboIndexReader _boboReader = null;
  private List<IndexReader> _subReaderList = null;
  private List<BoboIndexReader> _boboSubReaderList = null;

  private Long _timeAfter;

  public BrowserSortingPruningTest(String testName)
  {
    super(testName);
    _documentSize = 100;
    _segmentSize = 10;

    String confdir = System.getProperty("conf.dir");
    if (confdir == null)
      confdir = "./resource";
    org.apache.log4j.PropertyConfigurator.configure(confdir + "/log4j.properties");
  }

  public void testPruningNoSortingWithOneMultiReader() throws Exception
  {
    log.info("\n\n Starting test of testPruningNoSortingWithOneMultiReader");

    buildIndex();
    getAllReaders(true);

    BrowseRequest br = new BrowseRequest();
    br.setCount(100);
    br.setOffset(0);
    br.setQuery(new TermQuery(new Term("color", "red")));

    FacetSpec facetSpec = new FacetSpec();
    facetSpec.setMaxCount(100);
    facetSpec.setMinHitCount(1);
    facetSpec.setExpandSelection(true);
    facetSpec.setOrderBy(FacetSortSpec.OrderValueAsc);
    br.setFacetSpec("color", facetSpec);
    br.setFacetSpec("id", facetSpec);

    // there are seg_0, seg_1,.., seg_9, the _timeAfter is some time in seg_4
    // whose lastest time is newer than _timeAfter;
    // thus, the final segments should be all seg_N where N >=4; all hits are of
    // even id
    Pruner pruner = new BoboIndexReaderTimeBasedPruner("time", _timeAfter);
    BoboBrowser boboBrowser = new BoboBrowser(_boboReader, null, pruner);
    BrowseResult result = null;
    try
    {
      result = boboBrowser.browse(br);

      int expectedHitNum = 30; // 6 segments of even ids = 30
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      int k = 0;
      for (int i = 4; i < 10; i++)
      {
        for (int j = 0; j < 10; j += 2, k++)
        {
          int id = Integer.valueOf(hits[k].getField("id"));
          assertEquals(id, i * 10 + j);
          buffer.append("id=" + hits[k].getField("id") + "," + "color=" + hits[k].getField("color") + "\n");
        }
      }
      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (boboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          boboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  public void testPruningNoSortingWithMultipleSubReaders() throws Exception
  {
    log.info("\n\n Starting test of testPruningNoSortingWithMultipleSubReaders");

    buildIndex();
    getAllReaders(true);

    BrowseRequest br = new BrowseRequest();
    br.setCount(100);
    br.setOffset(0);
    br.setQuery(new TermQuery(new Term("color", "red")));

    FacetSpec facetSpec = new FacetSpec();
    facetSpec.setMaxCount(100);
    facetSpec.setMinHitCount(1);
    facetSpec.setExpandSelection(true);
    facetSpec.setOrderBy(FacetSortSpec.OrderValueAsc);
    br.setFacetSpec("color", facetSpec);
    br.setFacetSpec("id", facetSpec);

    // there are seg_0, seg_1,.., seg_9, the _timeAfter is some time in seg_4
    // whose lastest time is newer than _timeAfter;
    // thus, the final segments should be all seg_N where N >=4; all hits are of
    // even id
    Pruner pruner = new BoboIndexReaderTimeBasedPruner("time", _timeAfter);
    BoboBrowser boboBrowser = new BoboBrowser(_boboSubReaderList, null, pruner);
    BrowseResult result = null;
    try
    {
      result = boboBrowser.browse(br);

      int expectedHitNum = 30;
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      int k = 0;
      for (int i = 4; i < 10; i++)
      {
        for (int j = 0; j < 10; j += 2, k++)
        {
          int id = Integer.valueOf(hits[k].getField("id"));
          assertEquals(id, i * 10 + j);
          buffer.append("id=" + hits[k].getField("id") + "," + "color=" + hits[k].getField("color") + "\n");
        }
      }
      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (boboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          boboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  public void testSortingNoPruningWithOneMultiReader() throws Exception
  {
    log.info("\n\n Starting test of testSortingNoPruningWithOneMultiReader");

    buildIndex();
    getAllReaders(true);

    BrowseRequest br = new BrowseRequest();
    br.setCount(100);
    br.setOffset(0);
    br.setQuery(new TermQuery(new Term("color", "red")));

    FacetSpec facetSpec = new FacetSpec();
    facetSpec.setMaxCount(100);
    facetSpec.setMinHitCount(1);
    facetSpec.setExpandSelection(true);
    facetSpec.setOrderBy(FacetSortSpec.OrderValueAsc);
    br.setFacetSpec("color", facetSpec);
    br.setFacetSpec("id", facetSpec);

    // we sort all segments in reversed order by time, thus the seg_0 ->
    // seg_rev_9, ..., seg_4 -> seg_rev_5, seg_6->seg_rev_3,
    // seg_8 -> seg_rev_1. Thus, after sorting, the hits will still contain
    // results from seg_4 to seg_9. However, the hits are ordered
    // in first seg_9

    BoboIndexReaderComparator comparator = new BoboReaderTimeBasedComparator("time", true);
    BoboBrowser boboBrowser = new BoboBrowser(_boboReader, comparator, null);
    BrowseResult result = null;
    try
    {
      result = boboBrowser.browse(br);

      int expectedHitNum = 50;
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      int k = 0;
      for (int i = 9; i > 0; i--)
      {
        for (int j = 0; j < 10; j += 2, k++)
        {
          int id = Integer.valueOf(hits[k].getField("id"));
          assertEquals(id, i * 10 + j);
          buffer.append("id=" + hits[k].getField("id") + "," + "color=" + hits[k].getField("color") + "\n");
        }
      }

      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (boboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          boboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  public void testSortingAndPruningWithOneMultiReader() throws Exception
  {
    log.info("\n\n Starting test of testSortingAndPruningWithOneMultiReader");

    buildIndex();
    getAllReaders(true);

    BrowseRequest br = new BrowseRequest();
    br.setCount(100);
    br.setOffset(0);
    br.setQuery(new TermQuery(new Term("color", "red")));

    FacetSpec facetSpec = new FacetSpec();
    facetSpec.setMaxCount(100);
    facetSpec.setMinHitCount(1);
    facetSpec.setExpandSelection(true);
    facetSpec.setOrderBy(FacetSortSpec.OrderValueAsc);
    br.setFacetSpec("color", facetSpec);
    br.setFacetSpec("id", facetSpec);

    // there are seg_0, seg_1,.., seg_9, the _timeAfter is some time in seg_4
    // whose lastest time is newer than _timeAfter;
    // thus, the final segments should be all seg_N where N >=4; all hits are of
    // even id
    // we sort all segments in reversed order by time, thus the seg_0 ->
    // seg_rev_9, ..., seg_4 -> seg_rev_5, seg_6->seg_rev_3,
    // seg_8 -> seg_rev_1. Thus, after sorting, the hits will still contain
    // results from seg_4 to seg_9. However, the hits are ordered
    // in first seg_9

    Pruner pruner = new BoboIndexReaderTimeBasedPruner("time", _timeAfter);
    BoboIndexReaderComparator comparator = new BoboReaderTimeBasedComparator("time", true);
    BoboBrowser boboBrowser = new BoboBrowser(_boboReader, comparator, pruner);
    BrowseResult result = null;
    try
    {
      result = boboBrowser.browse(br);

      int expectedHitNum = 30;
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      int k = 0;
      for (int i = 9; i > 3; i--)
      {
        for (int j = 0; j < 10; j += 2, k++)
        {
          int id = Integer.valueOf(hits[k].getField("id"));
          assertEquals(id, i * 10 + j);
          buffer.append("id=" + hits[k].getField("id") + "," + "color=" + hits[k].getField("color") + "\n");
        }
      }

      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (boboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          boboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  public void testNoSortingNoPruningMultiReaders() throws Exception
  {
    log.info("\n\n  --------- Starting test of testNoSortingNoPruningMultiReaders -----------");

    buildIndex();
    BrowseRequest br = buildSimpleBrowseRequest();
    getAllReaders(true);

    Browsable[] browsables = BoboBrowser.createBrowsables(_boboSubReaderList);
    MultiBoboBrowser multiBoboBrowser = new MultiBoboBrowser(browsables);
    BrowseResult result = null;
    try
    {
      int expectedHitNum = 5;
      result = multiBoboBrowser.browse(br);
      log.info("expectedNum: " + expectedHitNum + ", hitsNum: " + result.getNumHits());
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      for (int i = 0; i < hits.length; ++i)
      {
        buffer.append("id=" + hits[i].getField("id") + "," + "name=" + hits[i].getField("name") + hits[i].getField("color") + "\n");
      }
      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (multiBoboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          multiBoboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  public void testNoSortingNoPruningSingleReader() throws Exception
  {
    log.info("\n\n  ------- Starting test of testNoSortingNoPruningSingleReader ---------");

    buildIndex();
    BrowseRequest br = buildSimpleBrowseRequest();
    getAllReaders(true);

    BoboBrowser boboBrowser = new BoboBrowser(_boboReader);
    BrowseResult result = null;
    try
    {
      int expectedHitNum = 5;
      result = boboBrowser.browse(br);
      log.info("expectedNum: " + expectedHitNum + ", hitsNum: " + result.getNumHits());
      assertEquals(expectedHitNum, result.getNumHits());

      StringBuilder buffer = new StringBuilder();
      BrowseHit[] hits = result.getHits();
      for (int i = 0; i < hits.length; ++i)
      {
        buffer.append("id=" + hits[i].getField("id") + "," + "color=" + hits[i].getField("color") + "\n");
      }
      log.info(buffer.toString());

    } catch (BrowseException e)
    {
      e.printStackTrace();
    } finally
    {
      if (boboBrowser != null)
      {
        try
        {
          if (result != null)
            result.close();
          boboBrowser.close();
        } catch (IOException e)
        {
          e.printStackTrace();
        }
      }
      if (_deleteDirectory)
        deleteDirectory(new File(_idxDir));
    }
  }

  private BrowseRequest buildSimpleBrowseRequest()
  {
    BrowseRequest br = new BrowseRequest();
    br.setCount(100);
    br.setOffset(0);
    br.setQuery(new TermQuery(new Term("color", "red")));

    FacetSpec facetSpec = new FacetSpec();
    facetSpec.setMaxCount(100);
    facetSpec.setMinHitCount(1);
    facetSpec.setExpandSelection(true);
    facetSpec.setOrderBy(FacetSortSpec.OrderValueAsc);
    br.setFacetSpec("color", facetSpec);
    br.setFacetSpec("id", facetSpec);

    BrowseSelection idSel = new BrowseSelection("id");
    idSel.addValue("2");
    idSel.addValue("12");
    idSel.addValue("22");
    idSel.addValue("32");
    idSel.addValue("42");
    br.addSelection(idSel);

    return br;
  }

  /**
   * Build a index with multiple segments. There are _documentSize documents in
   * the index. Each segment has _segmentSize documents.
   * 
   * @throws IOException
   */
  private void buildIndex() throws IOException
  {
    Directory dir = FSDirectory.open(new File(_idxDir));
    IndexWriter writer = new IndexWriter(dir, new StandardAnalyzer(Version.LUCENE_CURRENT), MaxFieldLength.UNLIMITED);
    writer.setMergeFactor(10000); // no merge
    writer.setMaxBufferedDocs(_segmentSize); // each segment has each
                                             // _segmentSize documents
    int i, k;
    for (i = 0; i < _documentSize;)
    {
      for (k = 0; k < _segmentSize && i < _documentSize; i++, k++)
      {
        String color = (i % 2 == 0) ? "red" : "green";
        String ID = Integer.toString(i);
        Document doc = new Document();
        long time = new Date().getTime();
        // set the _timeAfter as some time in the fifth segment
        if (i == 43)
        {
          _timeAfter = time;
        }
        doc.add(new Field("id", ID, Field.Store.NO, Index.NOT_ANALYZED_NO_NORMS));
        doc.add(new Field("time", String.valueOf(time), Field.Store.NO, Index.NOT_ANALYZED_NO_NORMS));
        doc.add(new Field("color", color, Field.Store.NO, Index.NOT_ANALYZED_NO_NORMS));
        writer.addDocument(doc);
        try
        {
          Thread.currentThread().sleep(10); // this is to get different time for
                                            // "time" field
        } catch (InterruptedException e)
        {
          log.error(e);
        }
      }
    }
    writer.close();
    dir.close();
  }

  private List<FacetHandler<?>> buildFacetHandlers()
  {
    List<FacetHandler<?>> facetHandlers = new ArrayList<FacetHandler<?>>();
    facetHandlers.add(new SimpleFacetHandler("id"));
    facetHandlers.add(new SimpleFacetHandler("color"));
    facetHandlers.add(new SimpleFacetHandler("time"));
    return facetHandlers;
  }

  private boolean deleteDirectory(File path)
  {
    if (path.exists())
    {
      File[] files = path.listFiles();
      for (int i = 0; i < files.length; i++)
      {
        if (files[i].isDirectory())
        {
          deleteDirectory(files[i]);
        } else
        {
          files[i].delete();
        }
      }
    }
    return (path.delete());
  }

  /**
   * Gather _reader/_boboReaders and _subReaderList/_boboSubReaderList
   * 
   * @param readonly
   * @throws IOException
   */
  private void getAllReaders(boolean readonly) throws IOException
  {
    if (_reader != null)
      return; // always return the opened reader if has been opened

    IndexReader _reader = IndexReader.open(_idxDir, readonly);
    List<FacetHandler<?>> facetHandlers = buildFacetHandlers();
    try
    {
      _boboReader = BoboIndexReader.getInstance(_reader, facetHandlers, null);
    } catch (IOException ioe)
    {
      if (_reader != null)
      {
        _reader.close();
      }
      throw ioe;
    }

    _boboSubReaderList = new ArrayList<BoboIndexReader>();
    ReaderUtil.gatherSubReaders(_boboSubReaderList, _boboReader);
    _subReaderList = new ArrayList<IndexReader>();
    for (int i = 0; i < _boboSubReaderList.size(); i++)
    {
      _subReaderList.add(_boboSubReaderList.get(i).getInnerReader());
    }
  }

}