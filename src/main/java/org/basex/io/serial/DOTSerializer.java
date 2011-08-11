package org.basex.io.serial;

import static org.basex.data.DataText.*;
import static org.basex.io.serial.DOTData.*;
import static org.basex.util.Token.*;

import java.io.IOException;

import org.basex.data.ExprInfo;
import org.basex.io.out.PrintOutput;
import org.basex.util.TokenBuilder;
import org.basex.util.Util;
import org.basex.util.list.IntList;
import org.basex.util.list.ObjList;

/**
 * This class serializes trees in the DOT syntax.
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 */
public final class DOTSerializer extends Serializer {
  /** Compact representation. */
  private final boolean compact;
  /** Output stream. */
  private final PrintOutput out;

  /** Cached children. */
  private final ObjList<IntList> children = new ObjList<IntList>();
  /** Cached attributes. */
  private final TokenBuilder tb = new TokenBuilder();
  /** Cached nodes. */
  private final IntList nodes = new IntList();

  /** Current color. */
  private String color;
  /** Node counter. */
  private int count;

  /**
   * Constructor, defining colors for the dot output.
   * @param o output stream
   * @param c compact representation
   * @throws IOException I/O exception
   */
  public DOTSerializer(final PrintOutput o, final boolean c)
      throws IOException {
    out = o;
    compact = c;
    out.println(HEADER);
  }

  @Override
  protected void startOpen(final byte[] t) {
    tb.reset();
  }

  @Override
  public void attribute(final byte[] n, final byte[] v) {
    tb.addExt(DOTATTR, DOTData.name(string(n)), v);
  }

  @Override
  public void finishOpen() throws IOException {
    final byte[] attr = tb.finish();
    if(color == null) color = DOTData.color(string(tag));
    if(color == null) color = attr.length == 0 ? DOTData.ELEM1 : DOTData.ELEM2;
    print(concat(tag, attr), color);
  }

  @Override
  public void finishClose(final boolean empty) throws IOException {
    if(empty) finishOpen();
    final int c = nodes.get(level());
    final IntList il = child(level());
    final int is = il.size();
    for(int i = 0; i < is; ++i) out.println(Util.info(DOTLINK, c, il.get(i)));
    color = null;
    il.reset();
  }

  @Override
  public void finishText(final byte[] t) throws IOException {
    print(norm(t), DOTData.TEXT);
  }

  @Override
  public void finishComment(final byte[] t) throws IOException {
    print(new TokenBuilder(COMM_O).add(norm(t)).add(COMM_C).finish(),
        DOTData.COMM);
  }

  @Override
  public void finishPi(final byte[] n, final byte[] v) throws IOException {
    print(new TokenBuilder(PI_O).add(n).add(SPACE).add(v).add(PI_C).finish(),
        DOTData.PI);
  }

  @Override
  public void finishItem(final byte[] t) throws IOException {
    print(norm(t), DOTData.ITEM);
  }

  @Override
  public void cls() throws IOException {
    out.println(FOOTER);
  }

  /**
   * Prints a single node.
   * @param t text
   * @param col color
   * @throws IOException I/O exception
   */
  private void print(final byte[] t, final String col) throws IOException {
    String txt = string(chop(t, 60)).replaceAll("\"|\\r|\\n", "'");
    if(compact) {
      txt = txt.replaceAll("\\\\n\\w+:", "\\\\n");
    }
    out.println(Util.info(DOTNODE, count, txt, col));
    nodes.set(level(), count);
    if(level() > 0) child(level() - 1).add(count);
    ++count;
  }

  /**
   * Returns the children from the stack.
   * @param i index
   * @return children
   */
  private IntList child(final int i) {
    while(i >= children.size()) children.add(new IntList());
    return children.get(i);
  }

  @Override
  protected byte[] name(final ExprInfo expr) {
    color = DOTData.color(expr);
    return token(DOTData.name(expr));
  }
}
