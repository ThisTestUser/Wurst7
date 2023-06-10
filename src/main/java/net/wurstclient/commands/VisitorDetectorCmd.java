/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.WurstClient;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.json.JsonUtils;

public final class VisitorDetectorCmd extends Command implements UpdateListener
{
	private final File folder = 
		WurstClient.INSTANCE.getWurstFolder().resolve("visitordetector").toFile();
	private LogoutData serverData;
	private String address;
	private boolean waitingForEntity;
	private long firstMobTime;
	private boolean warnPos;
	
	public VisitorDetectorCmd()
	{
		super("visitordetector", "Determines if your region was loaded\n"
			+ "while you were offline. Include the checkpos option if the server\n"
			+ "has a queue server.",
			".visitordetector [list]", ".visitordetector checkpos");
	}

	@Override
	public void call(String[] args) throws CmdException
	{
		boolean checkPos = false;
		if(args.length == 1 && args[0].equalsIgnoreCase("list"))
		{
			int count = 0;
			List<String> servers = new ArrayList<>();
			if(!folder.exists() || folder.listFiles().length == 0)
				count = 0;
			for(File file : folder.listFiles())
				if(file.getName().endsWith(".json"))
				{
					servers.add(file.getName().replace('_', '.').replace(":", "_").substring(0, file.getName().length() - 5));
					count++;
				}
			ChatUtils.message("Servers where VisitorDetector is active: " + count);
			servers.forEach(s -> ChatUtils.message(s));
			return;
		}else if(args.length == 1 && (args[0].equalsIgnoreCase("checkpos")
			|| args[0].equalsIgnoreCase("poscheck")))
			checkPos = true;
		else if(args.length != 0)
			throw new CmdSyntaxError();
		if(MC.isInSingleplayer())
			throw new CmdError("VisitorDetector is intended for servers!");
		List<Entity> entities = new ArrayList<>();
		Entity[] entitiesPerAxis = new Entity[4];
		double[] distances = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
			Double.MAX_VALUE};
		Vec3d playerPos = MC.player.getPos();
		//Diagonals
		for(Entity en : MC.world.getEntities())
			if(en instanceof LivingEntity && !(en instanceof PlayerEntity))
			{
				int index = 0;
				if(en.getX() > playerPos.x)
					index += 1;
				if(en.getZ() > playerPos.z)
					index += 2;
				double squaredDist = squaredDiff(en.getX(), playerPos.x)
					+ squaredDiff(en.getZ(), playerPos.z) + Math.random() * 10;
				if(distances[index] > squaredDist)
					entitiesPerAxis[index] = en;
			}
		for(Entity en : entitiesPerAxis)
			if(en != null)
				entities.add(en);
		entitiesPerAxis = new Entity[4];
		distances = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
			Double.MAX_VALUE};
		//Cardinals
		for(Entity en : MC.world.getEntities())
			if(!entities.contains(en) && en instanceof LivingEntity && !(en instanceof PlayerEntity))
			{
				int index = -1;
				if(Math.abs(en.getX() - playerPos.x) < 5 && Math.abs(en.getZ() - playerPos.z) > 5)
					if(en.getZ() > playerPos.z)
						index = 0;
					else
						index = 1;
				if(Math.abs(en.getX() - playerPos.x) > 5 && Math.abs(en.getZ() - playerPos.z) < 5)
					if(en.getX() > playerPos.x)
						index = 2;
					else
						index = 3;
				if(index == -1)
					continue;
				double squaredDist = squaredDiff(en.getX(), playerPos.x)
					+ squaredDiff(en.getZ(), playerPos.z) + Math.random() * 10;
				if(distances[index] > squaredDist)
					entitiesPerAxis[index] = en;
			}
		for(Entity en : entitiesPerAxis)
			if(en != null)
				entities.add(en);
		if(entities.isEmpty())
			throw new CmdError("No mobs were detected in render distance!");
		File serverFile = new File(folder, MC.getCurrentServerEntry().address.replace(".", "_").replace(":", "_") + ".json");
		folder.mkdirs();
		boolean exists = serverFile.exists();
		try
		{
			serverFile.createNewFile();
		}catch(IOException e)
		{
			e.printStackTrace();
			throw new CmdError("Failed to create VisitorDetector file!");
		}
		List<EntityEntry> entityInfo = new ArrayList<>();
		for(Entity en : entities)
			entityInfo.add(new EntityEntry(en.getType().getUntranslatedName(), en.getX(), en.getY(), en.getZ(),
				en.getYaw(), en.getPitch()));
		LogoutData data = new LogoutData(MC.player.getUuid().toString(),
			playerPos.x, playerPos.y, playerPos.z, checkPos, entityInfo);
		try
		{
			try(PrintWriter save = new PrintWriter(new FileWriter(serverFile)))
			{
				save.println(JsonUtils.PRETTY_GSON.toJson(data));
			}
		}catch(Exception e)
		{
			e.printStackTrace();
			throw new CmdError("Error while writing to VisitorDetector file!");
		}
		MC.getNetworkHandler().getConnection().disconnect(Text.literal(
			"VisitorDetector successfully activated." + (exists ? " Note: A previous instance was overwritten." : "")));
	}
	
	public void onJoin()
	{
		if(MC.isInSingleplayer() || !folder.exists())
			return;
		//Force single instance
		if(address != null && MC.getCurrentServerEntry().address.equals(address))
		{
			waitingForEntity = true;
			return;
		}
		File serverFile = new File(folder, MC.getCurrentServerEntry().address.replace(".", "_").replace(":", "_") + ".json");
		if(!serverFile.exists())
			return;
		try
		{
			try(BufferedReader load = new BufferedReader(new FileReader(serverFile)))
			{
				LogoutData data = JsonUtils.GSON.fromJson(load, LogoutData.class);
				if(serverData != null)
				{
					ChatUtils.warning("An unfinished instance of VisitorDetector was terminated! "
						+ "IP: " + address);
					EVENTS.remove(UpdateListener.class, this);
				}
				serverData = data;
				address = MC.getCurrentServerEntry().address;
				warnPos = false;
				waitingForEntity = true;
				EVENTS.add(UpdateListener.class, this);
			}
		}catch(Exception e)
		{
			ChatUtils.warning("Failed to load VisitorDetector data, instance was not run!");
			e.printStackTrace();
			serverData = null;
			address = null;
			EVENTS.remove(UpdateListener.class, this);
		}
	}
	
	@Override
	public void onUpdate()
	{
		if(waitingForEntity)
			return;
		if(System.currentTimeMillis() < firstMobTime + 1000)
			return;
		if(MC.isInSingleplayer())
		{
			ChatUtils.warning(address + " was left before VisitorDetector finished running!");
			removeListeners();
			return;
		}
		if(MC.player == null || MC.world == null)
			return;
		if(!MC.player.getUuid().toString().equals(serverData.uuid))
		{
			ChatUtils.warning("VisitorDetector is active for this server, but will not run"
				+ " because the UUID saved does not match with the current UUID!");
			removeListeners();
			return;
		}
		if(serverData.checkPos && (Math.abs(MC.player.getX() - serverData.playerX) > 1
			|| Math.abs(MC.player.getY() - serverData.playerY) > 1
			|| Math.abs(MC.player.getZ() - serverData.playerZ) > 1))
		{
			if(!warnPos)
			{
				ChatUtils.warning("VisitorDetector is running but will wait "
					+ "until you are at your logout coordinates.");
				warnPos = true;
			}
			return;
		}
		List<Entity> verified = new ArrayList<>();
		int index = 1;
		for(EntityEntry entry : serverData.entityData)
		{
			Entity closestMatch = null;
			double distanceSq = -1;
			for(Entity en : MC.world.getEntities())
				if(!verified.contains(en) && en.getType().getUntranslatedName().equals(entry.type)
					&& en.squaredDistanceTo(entry.x, entry.y, entry.z) < 100)
				{
					float yawDiff = (en.getYaw() - entry.yaw) % 360;
					if(yawDiff < 0)
						yawDiff += 360;
					yawDiff = Math.min(yawDiff, (yawDiff + 360) % 360);
					double curDistSq = squaredDiff(en.getX(), entry.x) * 5
						+ squaredDiff(en.getY(), entry.y) * 5
						+ squaredDiff(en.getZ(), entry.z) * 5
						+ squaredDiff(en.getPitch(), entry.pitch) * 0.2
						+ Math.pow(yawDiff, 2) * 0.5;
					if(closestMatch == null || distanceSq > curDistSq)
					{
						closestMatch = en;
						distanceSq = curDistSq;
					}
				}
			if(closestMatch == null)
				ChatUtils.message(Formatting.RED + "Entity #" + index + " was not found!");
			else
			{
				ChatUtils.message("Entity #" + index + " discrepancies:");
				boolean anyFail = false;
				if(Math.abs(closestMatch.getX() - entry.x) > 0.1)
				{
					ChatUtils.message("X Position off by " + Math.abs(closestMatch.getX() - entry.x));
					anyFail = true;
				}
				if(Math.abs(closestMatch.getY() - entry.y) > 0.1)
				{
					ChatUtils.message("Y Position off by " + Math.abs(closestMatch.getY() - entry.y));
					anyFail = true;
				}
				if(Math.abs(closestMatch.getZ() - entry.z) > 0.1)
				{
					ChatUtils.message("Z Position off by " + Math.abs(closestMatch.getZ() - entry.z));
					anyFail = true;
				}
				if(Math.abs(closestMatch.getPitch() - entry.pitch) > 1.5)
				{
					ChatUtils.message("Pitch rotation off by " + Math.abs(closestMatch.getPitch() - entry.pitch));
					anyFail = true;
				}
				float yawDiff = Math.abs(MathHelper.wrapDegrees(closestMatch.getYaw()) - MathHelper.wrapDegrees(entry.yaw));
				if(yawDiff > 1)
				{
					ChatUtils.message("Yaw Position off by " + yawDiff);
					anyFail = true;
				}
				if(!anyFail)
					ChatUtils.message("None");
				verified.add(closestMatch);
			}
			index++;
		}
		ChatUtils.message("VisitorDetector completed. Note that only large changes in position or rotation "
			+ "indicate possible visitors.");
		new File(folder, MC.getCurrentServerEntry().address.replace(".", "_").replace(":", "_") + ".json").delete();
		removeListeners();
	}
	
	private double squaredDiff(double one, double two)
	{
		return Math.pow(one - two, 2);
	}
	
	private void removeListeners()
	{
		EVENTS.remove(UpdateListener.class, this);
		serverData = null;
		address = null;
	}
	
	public void checkEntityPackets()
	{
		waitingForEntity = true;
	}
	
	public void startTimer()
	{
		if(!waitingForEntity)
			return;
		waitingForEntity = false;
		firstMobTime = System.currentTimeMillis();
	}
	
	public void removeTimer()
	{
		waitingForEntity = false;
		firstMobTime = 0;
	}
	
	public class LogoutData
	{
		public final String uuid;
		public final double playerX;
		public final double playerY;
		public final double playerZ;
		public final boolean checkPos;
		public final List<EntityEntry> entityData;
		
		public LogoutData(String uuid, double playerX, double playerY, double playerZ,
			boolean checkPos, List<EntityEntry> entityData)
		{
			this.uuid = uuid;
			this.playerX = playerX;
			this.playerY = playerY;
			this.playerZ = playerZ;
			this.checkPos = checkPos;
			this.entityData = entityData;
		}
	}
	
	public class EntityEntry
	{
		public final String type;
		public final double x;
		public final double y;
		public final double z;
		public final float yaw;
		public final float pitch;
		
		public EntityEntry(String type, double x, double y, double z, float yaw, float pitch)
		{
			this.type = type;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}
}
