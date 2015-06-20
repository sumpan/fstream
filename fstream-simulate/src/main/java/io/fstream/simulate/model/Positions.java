package io.fstream.simulate.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.val;

/**
 * Class providing accounting services to trading agents for position tracking
 */
@Getter
@Setter
@Deprecated
// TODO: Remove?
public class Positions {

  private Map<String, List<Order>> positions = new HashMap<>();

  public List<Order> getPositions(String symbol) {
    return positions.get(symbol);
  }

  public int getPositionSize(String symbol) {
    int size = 0;
    val symbolpos = positions.get(symbol);
    if (symbolpos == null || symbolpos.isEmpty()) {
      return size;
    }
    for (val order : symbolpos) {
      size = size + order.getAmount();
    }
    return size;
  }

  public boolean addPosition(Order order) {
    if (positions.get(order.getSymbol()) != null) {
      return positions.get(order.getSymbol()).add(order);
    } else {
      val orderlist = new ArrayList<Order>();
      orderlist.add(order);
      positions.put(order.getSymbol(), orderlist);
      return true;
    }
  }

}