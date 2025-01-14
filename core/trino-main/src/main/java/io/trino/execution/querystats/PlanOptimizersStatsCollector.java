/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.execution.querystats;

import io.trino.spi.eventlistener.QueryPlanOptimizerStatistics;
import io.trino.sql.planner.iterative.Rule;
import io.trino.sql.planner.optimizations.PlanOptimizer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;

public class PlanOptimizersStatsCollector
{
    private final Map<Class<?>, QueryPlanOptimizerStats> stats = new HashMap<>();
    private final int queryReportedRuleStatsLimit;

    public PlanOptimizersStatsCollector(int queryReportedRuleStatsLimit)
    {
        this.queryReportedRuleStatsLimit = queryReportedRuleStatsLimit;
    }

    public void recordRule(Rule<?> rule, boolean invoked, boolean applied, long elapsedNanos)
    {
        if (invoked) {
            stats.computeIfAbsent(rule.getClass(), (key) -> new QueryPlanOptimizerStats(key.getCanonicalName()))
                    .record(elapsedNanos, applied);
        }
    }

    public void recordOptimizer(PlanOptimizer planOptimizer, long duration)
    {
        stats.computeIfAbsent(planOptimizer.getClass(), (key) -> new QueryPlanOptimizerStats(key.getCanonicalName()))
                .record(duration, true);
    }

    public void recordFailure(Rule<?> rule)
    {
        stats.computeIfAbsent(rule.getClass(), (key) -> new QueryPlanOptimizerStats(key.getCanonicalName()))
                .recordFailure();
    }

    public void recordFailure(PlanOptimizer rule)
    {
        stats.computeIfAbsent(rule.getClass(), (key) -> new QueryPlanOptimizerStats(key.getCanonicalName()))
                .recordFailure();
    }

    public List<QueryPlanOptimizerStatistics> getTopRuleStats()
    {
        return getTopRuleStats(queryReportedRuleStatsLimit);
    }

    public List<QueryPlanOptimizerStatistics> getTopRuleStats(int limit)
    {
        return stats.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Class<?>, QueryPlanOptimizerStats>, Long>comparing(entry -> entry.getValue().getTotalTime()).reversed())
                .limit(limit)
                .map((Map.Entry<Class<?>, QueryPlanOptimizerStats> entry) -> entry.getValue().snapshot(entry.getKey().getCanonicalName()))
                .collect(toImmutableList());
    }

    public void add(PlanOptimizersStatsCollector collector)
    {
        collector.stats.entrySet().stream()
                .forEach(entry -> this.stats.computeIfAbsent(entry.getKey(), key -> new QueryPlanOptimizerStats(key.getCanonicalName())).merge(entry.getValue()));
    }

    public static PlanOptimizersStatsCollector createPlanOptimizersStatsCollector()
    {
        return new PlanOptimizersStatsCollector(Integer.MAX_VALUE);
    }
}
