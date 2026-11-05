package com.example.charteams;

import com.example.charteams.model.Team;
import com.example.charteams.model.TeamBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

// The teams, saved with the overworld (data/charteams.dat).
public class TeamData extends SavedData {
    static final String NAME = "charteams";
    final TeamBook book = new TeamBook();

    TeamData() {
        book.onChange(this::setDirty);
    }

    static TeamData load(CompoundTag tag) {
        TeamData data = new TeamData();
        ListTag teams = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teams.size(); i++) {
            CompoundTag t = teams.getCompound(i);
            if (!t.hasUUID("id") || !t.hasUUID("owner")) continue;
            Map<UUID, String> members = new LinkedHashMap<>();
            ListTag list = t.getList("members", Tag.TAG_COMPOUND);
            for (int j = 0; j < list.size(); j++) {
                CompoundTag m = list.getCompound(j);
                if (m.hasUUID("id")) members.put(m.getUUID("id"), m.getString("name"));
            }
            data.book.restore(t.getUUID("id"), t.getString("name"), t.getInt("colour"), t.getUUID("owner"), members);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag teams = new ListTag();
        for (Team team : book.teams()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", team.id());
            t.putString("name", team.name());
            t.putInt("colour", team.colour());
            t.putUUID("owner", team.owner());
            ListTag list = new ListTag();
            for (Map.Entry<UUID, String> e : team.members().entrySet()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("id", e.getKey());
                m.putString("name", e.getValue());
                list.add(m);
            }
            t.put("members", list);
            teams.add(t);
        }
        tag.put("teams", teams);
        return tag;
    }
}
