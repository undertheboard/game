package com.redistricting.ai;

import com.redistricting.model.District;
import com.redistricting.model.RedistrictingMap;

import java.util.List;

/**
 * Immutable summary of fairness metrics computed from a {@link RedistrictingMap}.
 *
 * <p>All scores are normalized so that <strong>lower is fairer</strong>; the
 * combined {@link #unfairnessScore} is a weighted sum used by the AI optimizer.
 */
public final class FairnessReport {

    private final double populationDeviation; // max |pop - ideal| / ideal, 0 = perfect
    private final double avgCompactness;      // mean Polsby-Popper, 1 = circle, 0 = degenerate
    private final double efficiencyGap;       // signed efficiency gap [-1, 1], 0 = fair
    private final double unfairnessScore;     // combined, lower = fairer
    private final List<District> districts;

    public FairnessReport(double populationDeviation, double avgCompactness,
                          double efficiencyGap, double unfairnessScore,
                          List<District> districts) {
        this.populationDeviation = populationDeviation;
        this.avgCompactness = avgCompactness;
        this.efficiencyGap = efficiencyGap;
        this.unfairnessScore = unfairnessScore;
        this.districts = districts;
    }

    public double populationDeviation() { return populationDeviation; }
    public double avgCompactness() { return avgCompactness; }
    public double efficiencyGap() { return efficiencyGap; }
    public double unfairnessScore() { return unfairnessScore; }
    public List<District> districts() { return districts; }

    public String prettyPrint(String mapName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fairness report for: ").append(mapName).append('\n');
        sb.append(String.format("  Population deviation : %.2f%% (lower is fairer)%n",
                populationDeviation * 100));
        sb.append(String.format("  Avg. compactness     : %.3f  (Polsby-Popper, 1.0 = circle)%n",
                avgCompactness));
        sb.append(String.format("  Efficiency gap       : %+.2f%% (0 = balanced)%n",
                efficiencyGap * 100));
        sb.append(String.format("  Combined unfairness  : %.4f  (lower is better)%n",
                unfairnessScore));
        sb.append('\n').append("Per-district summary:").append('\n');
        for (District d : districts) {
            int dem = d.totalDemVotes();
            int rep = d.totalRepVotes();
            String winner = d.winner() == 0 ? "Dem" : d.winner() == 1 ? "Rep" : "Tie";
            sb.append(String.format(
                    "  D%d  pop=%-7d  dem=%-6d  rep=%-6d  winner=%s%n",
                    d.id(), d.totalPopulation(), dem, rep, winner));
        }
        return sb.toString();
    }
}
