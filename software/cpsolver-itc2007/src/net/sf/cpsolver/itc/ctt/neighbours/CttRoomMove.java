package net.sf.cpsolver.itc.ctt.neighbours;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.ToolBox;
import net.sf.cpsolver.itc.ctt.model.CttLecture;
import net.sf.cpsolver.itc.ctt.model.CttModel;
import net.sf.cpsolver.itc.ctt.model.CttPlacement;
import net.sf.cpsolver.itc.ctt.model.CttRoom;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcSimpleNeighbour;
import net.sf.cpsolver.itc.heuristics.search.ItcHillClimberSeq.HillClimberSelection;

/**
 * A room is changed for a randomly selected lecture.
 * First not conflicting room after a randomly selected one is returned.
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
public class CttRoomMove implements NeighbourSelection<CttLecture, CttPlacement>, HillClimberSelection {
    private boolean iHC = false;
    
    /** Constructor */
    public CttRoomMove(DataProperties properties) {}
    /** Initialization */
    public void init(Solver<CttLecture, CttPlacement> solver) {}
    /** Set hill-climber mode (worsening moves are skipped) */
    public void setHcMode(boolean hcMode) { iHC = hcMode; }
    
    /** Neighbour selection */
    public Neighbour<CttLecture, CttPlacement> selectNeighbour(Solution<CttLecture, CttPlacement> solution) {
        CttModel model = (CttModel)solution.getModel();
        // get random lecture
        CttLecture lecture = (CttLecture)ToolBox.random(model.variables());
        CttPlacement placement = (CttPlacement)lecture.getAssignment();
        // Get day and slot of lecture or rand slot
        int day = (placement==null?ToolBox.random(model.getNrDays()):placement.getDay());
        int slot = (placement==null?ToolBox.random(model.getNrSlotsPerDay()):placement.getSlot());
        // Check its okay
        if (placement==null && !lecture.getCourse().isAvailable(day, slot)) return null;
        // Get rand room
        int rx = ToolBox.random(model.getRooms().size());
        // All rooms
        for (int r=0;r<model.getRooms().size();r++) {
            // Get size of rand room
            CttRoom room = model.getRooms().get((r+rx)%model.getRooms().size());
            // Get placement of lecture in room
            CttPlacement conflict = (CttPlacement)room.getConstraint().getPlacement(day, slot);
            // If okay
            if (conflict==null) {
                // Return simple neighbour
                ItcSimpleNeighbour<CttLecture, CttPlacement> n = new ItcSimpleNeighbour<CttLecture, CttPlacement>(lecture, new CttPlacement(lecture, room, day, slot));
                if (!iHC || n.value()<=0) return n;
            }
        }
        return null;
    }
}
