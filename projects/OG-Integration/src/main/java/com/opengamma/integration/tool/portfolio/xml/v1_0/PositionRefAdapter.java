package com.opengamma.integration.tool.portfolio.xml.v1_0;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class PositionRefAdapter extends XmlAdapter<PositionRef, Position> {

  @Override
  public Position unmarshal(PositionRef positionRef) throws Exception {
    return positionRef.getPosition();
  }

  @Override
  public PositionRef marshal(Position position) throws Exception {
    return new PositionRef(position);
  }
}