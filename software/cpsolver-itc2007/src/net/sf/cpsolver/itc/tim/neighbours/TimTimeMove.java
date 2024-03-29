package net.sf.cpsolver.itc.tim.neighbours;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.ToolBox;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcSimpleNeighbour;
import net.sf.cpsolver.itc.heuristics.search.ItcHillClimberSeq.HillClimberSelection;
import net.sf.cpsolver.itc.tim.model.TTComp02Model;
import net.sf.cpsolver.itc.tim.model.TimEvent;
import net.sf.cpsolver.itc.tim.model.TimLocation;
import net.sf.cpsolver.itc.tim.model.TimRoom;
import net.sf.cpsolver.itc.tim.model.TimStudent;

/**
 * Move a randomly selected event into a different time.
 * 
 * @version
 * ITC2007 1.0<br>
 * Copyright (C) 2007 Tomas Muller<br>
 * <a href="mailto:muller@unitime.org">muller@unitime.org</a><br>
 * <a href="http://muller.unitime.org">http://muller.unitime.org</a><br>
 * <br>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * <br><br>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <br><br>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not see
 * <a href='http://www.gnu.org/licenses/'>http://www.gnu.org/licenses/</a>.
 */
public class TimTimeMove implements NeighbourSelection<TimEvent, TimLocation>, HillClimberSelection {
    private boolean iHC = false;
    
    /** Constructor */
    public TimTimeMove(DataProperties properties) {
    }
    /** Initialization */
    public void init(Solver<TimEvent, TimLocation> solver) {}
    /** Set hill-climber mode (worsening moves are skipped) */
    public void setHcMode(boolean hcMode) { iHC = hcMode; }

    /** Neighbour selection */
    public Neighbour<TimEvent, TimLocation> selectNeighbour(Solution<TimEvent, TimLocation> solution) {
        TTComp02Model model = (TTComp02Model)solution.getModel();
        TimEvent event = (TimEvent)ToolBox.random(model.variables());
        TimRoom room = (event.getAssignment()==null?
                (TimRoom)ToolBox.random(event.rooms()):
                ((TimLocation)event.getAssignment()).room());
        if (room==null) room = (TimRoom)ToolBox.random(event.rooms());
        int tx = ToolBox.random(45);
        time: for (int t=0;t<45;t++) {
            int time = (t + tx) % 45;
            if (!event.isAvailable(time)) continue;
            if (room.getLocation(time)!=null) continue;
            for (TimStudent student: event.students()) {
                if (student.getLocation(time)!=null) continue time;
            }
            Neighbour<TimEvent, TimLocation> n = new ItcSimpleNeighbour<TimEvent, TimLocation>(event, new TimLocation(event, time, room));
            if (!iHC || n.value()<=0) return n;
        }
        return null;
    }
}