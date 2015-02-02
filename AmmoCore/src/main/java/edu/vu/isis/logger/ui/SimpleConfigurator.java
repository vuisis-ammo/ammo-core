/* Copyright (c) 2010-2015 Vanderbilt University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package edu.vu.isis.logger.ui;
/**
 * Logback: the reliable, generic, fast and flexible logging framework.
 */

import java.util.List;
import java.util.Map;

import ch.qos.logback.core.joran.GenericConfigurator;
import ch.qos.logback.core.joran.action.Action;
import ch.qos.logback.core.joran.action.ImplicitAction;
import ch.qos.logback.core.joran.spi.Interpreter;
import ch.qos.logback.core.joran.spi.Pattern;
import ch.qos.logback.core.joran.spi.RuleStore;

/**
 * A minimal configurator extending GenericConfigurator.
 * 
 * @author Ceki G&uuml;c&uuml;
 *
 */
public class SimpleConfigurator extends GenericConfigurator {

  final Map<Pattern, Action> ruleMap;
  final List<ImplicitAction> iaList;

  public SimpleConfigurator(Map<Pattern, Action> ruleMap) {
    this(ruleMap, null);
  }

  public SimpleConfigurator(Map<Pattern, Action> ruleMap, List<ImplicitAction> iaList) {
    this.ruleMap = ruleMap;
    this.iaList = iaList;
  }

  @Override
  protected void addInstanceRules(RuleStore rs) {
    for (Pattern pattern : ruleMap.keySet()) {
      Action action = ruleMap.get(pattern);
      rs.addRule(pattern, action);
    }
  }

  @Override
  protected void addImplicitRules(Interpreter interpreter) {
    if(iaList == null) {
      return;
    }
    for (ImplicitAction ia : iaList) {
      interpreter.addImplicitAction(ia);
    }
  }

}
