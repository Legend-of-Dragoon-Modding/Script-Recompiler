package org.legendofdragoon.scripting.tokens;

import org.legendofdragoon.scripting.StringInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Script {
  public final Entry[] entries;
  public final Set<Integer> entrypoints = new HashSet<>();
  public final Set<Integer> branches = new HashSet<>();
  public final Set<Integer> subs = new HashSet<>();
  public final Set<Integer> subTables = new HashSet<>();
  public final Set<Integer> reentries = new HashSet<>();
  public final Set<Integer> jumpTableDests = new HashSet<>();
  public final Set<StringInfo> strings = new HashSet<>();
  public final Map<Integer, List<String>> labels = new HashMap<>();
  public final Map<String, Integer> labelUsageCount = new HashMap<>();
  /** Deferred list of string tables to build after looking for table overruns */
  public final List<Runnable> buildStrings = new ArrayList<>();
  private int labelCount;

  public Script(final int length) {
    this.entries = new Entry[length];
  }

  /** Uses an existing label if one already points to this address */
  public String addLabel(final int destAddress, final String name) {
    if(this.labels.containsKey(destAddress)) {
      final String existing = this.labels.get(destAddress).get(0);
      this.labelUsageCount.putIfAbsent(existing, 0);
      this.labelUsageCount.compute(existing, (label, value) -> value + 1);
      return existing;
    }

    this.labels.computeIfAbsent(destAddress, k -> new ArrayList<>()).add(name);
    this.labelUsageCount.putIfAbsent(name, 0);
    this.labelUsageCount.compute(name, (label, value) -> value + 1);
    this.labelCount++;
    return name;
  }

  /** Forces adding a label even if another label already points to this address */
  public String addUniqueLabel(final int destAddress, final String name) {
    this.labels.computeIfAbsent(destAddress, k -> new ArrayList<>()).add(name);
    this.labelCount++;
    return name;
  }

  public int getLabelCount() {
    return this.labelCount;
  }
}
