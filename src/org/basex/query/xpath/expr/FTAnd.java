package org.basex.query.xpath.expr;

import org.basex.query.QueryException;
import org.basex.query.xpath.XPContext;
import org.basex.query.xpath.locpath.Step;
import org.basex.query.xpath.values.Bool;
import org.basex.util.IntList;

/**
 * Logical FTAnd expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Sebastian Gath
 */
public final class FTAnd extends FTArrayExpr {
  /** Saving index of positive expressions. */
  private int[] pex;
  /** Saving index of negative expressions (FTNot). */
  private int[] nex;

  /**
   * Constructor.
   * @param e expressions
   */
  public FTAnd(final FTArrayExpr[] e) {
    exprs = e;
  }

  @Override
  public Bool eval(final XPContext ctx) throws QueryException {
    for(final Expr e : exprs) 
      if(!ctx.eval(e).bool()) 
        return Bool.get(false);
    
    
    return Bool.get(true);
  }

  @Override
  public FTArrayExpr compile(final XPContext ctx) throws QueryException {
    for(int i = 0; i != exprs.length; i++) {
      //if(exprs[i].fto == null) exprs[i].fto = fto;
      exprs[i] = exprs[i].compile(ctx);
    }
    return this;
  }

  @Override
  public FTArrayExpr indexEquivalent(final XPContext ctx, final Step curr,
      final boolean seq) throws QueryException {
    
    if (pex.length == 1 && nex.length == 0)
      exprs[pex[0]].indexEquivalent(ctx, curr, seq);

    final FTArrayExpr[] indexExprs = new FTArrayExpr[exprs.length];
    for (int i = 0; i < exprs.length; i++) {
      indexExprs[i] = exprs[i].indexEquivalent(ctx, curr, seq);
    }

    return new FTIntersection(indexExprs, pex, nex);
  }

  @Override
  public int indexSizes(final XPContext ctx, final Step curr, final int min) {
    final IntList i1 = new IntList();
    final IntList i2 = new IntList();
    int nmin = min;
    for (int i = 0; i < exprs.length; i++) {
      final int nrIDs = exprs[i].indexSizes(ctx, curr, min);
      if (!(exprs[i] instanceof FTUnaryNot)) {
        i1.add(i);
        nmin = nrIDs < nmin ? nrIDs : nmin;
      } else if (nrIDs > 0) {
        i2.add(i);
      }
    }
    pex = i1.finish();
    nex = i2.finish();

    return i1.size == 0 ? Integer.MAX_VALUE : nmin;
  }
}
