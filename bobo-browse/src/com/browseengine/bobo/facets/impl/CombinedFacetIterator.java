/**
 * 
 */
package com.browseengine.bobo.facets.impl;

import java.util.List;
import java.util.NoSuchElementException;

import org.apache.lucene.util.PriorityQueue;

import com.browseengine.bobo.api.FacetIterator;

/**
 * @author nnarkhed
 *
 */
public class CombinedFacetIterator implements FacetIterator {

  private String _facet;
  private int _count;

  private class IteratorNode
  {
    public FacetIterator _iterator;
    public String _curFacet;
    public int _curFacetCount;

    public IteratorNode(FacetIterator iterator)
    {
      _iterator = iterator;
      _curFacet = null;
      _curFacetCount = 0;
    }

    public boolean fetch()
    {
      if(_iterator.hasNext())
      {
        _curFacet = _iterator.next();
        _curFacetCount = _iterator.getFacetCount();
        return true;
      }
      _curFacet = null;
      _curFacetCount = 0;
      return false;
    }
    
    public String peek()
    {
      if(_iterator.hasNext()) 
      {
        return _iterator.getFacet();
      }
      return null;
    }
  }

  private final PriorityQueue _queue;

  private List<FacetIterator> _iterators;

  private CombinedFacetIterator(final int length) {
    _queue = new PriorityQueue() {
      {
        this.initialize(length);
      }
      @Override
      protected boolean lessThan(Object o1, Object o2) {
        String v1 = ((IteratorNode)o1)._curFacet;
        String v2 = ((IteratorNode)o2)._curFacet;

        return v1.compareTo(v2) < 0;
      }
    };		
  }

  public CombinedFacetIterator(final List<FacetIterator> iterators) {
    this(iterators.size());
    _iterators = iterators;
    for(FacetIterator iterator : iterators) {
      IteratorNode node = new IteratorNode(iterator);
      if(node.fetch())
        _queue.add(node);
    }
    _facet = null;
    _count = 0;
  }

  /* (non-Javadoc)
   * @see com.browseengine.bobo.api.FacetIterator#getFacet()
   */
  public String getFacet() {
    return _facet;
  }

  /* (non-Javadoc)
   * @see com.browseengine.bobo.api.FacetIterator#getFacetCount()
   */
  public int getFacetCount() {
    return _count;
  }

  /* (non-Javadoc)
   * @see com.browseengine.bobo.api.FacetIterator#next()
   */
  public String next() {
    if(!hasNext())
      throw new NoSuchElementException("No more facets in this iteration");

    IteratorNode node = (IteratorNode) _queue.top();
    
    _facet = node._curFacet;
    String next = null;
    _count = 0;
    while(hasNext())
    {
      node = (IteratorNode) _queue.top();
      next = node._curFacet;
      if( (next != null) && (!next.equals(_facet)) )
        break;
      _count += node._curFacetCount;
      if(node.fetch())
        _queue.updateTop();
      else
        _queue.pop();
    }
    return _facet;
  }

  /* (non-Javadoc)
   * @see java.util.Iterator#hasNext()
   */
  public boolean hasNext() {
    return (_queue.size() > 0);
  }

  /* (non-Javadoc)
   * @see java.util.Iterator#remove()
   */
  public void remove() {
    throw new UnsupportedOperationException("remove() method not supported for Facet Iterators");
  }

}