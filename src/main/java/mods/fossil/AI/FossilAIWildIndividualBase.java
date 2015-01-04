package mods.fossil.AI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

import mods.fossil.entity.mobs.EntityPrehistoric;
import mods.fossil.guiBlocks.TileEntityFeeder;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public abstract class FossilAIWildIndividualBase extends EntityAIBase {
	
	private static final int taskFleeNear = 0;
	private static final int taskFleeFar = 1;
	private static final int taskAvoid = 2;
	private static final int taskWanderFast = 3;
	private static final int taskWanderSlow = 4;
	private static final int taskFollow = 5;
	private static final int taskIdle = 6;
	private static final int taskTrackAndAttack = 7;
	private static final int taskFeedFromFeeder = 8;
	private static final int taskPickupItem = 9;
	private static final int taskEatBlock = 10;
	private static final int taskFormHerd = 11;
	private static final int taskAskJoinHerd = 12;
	private static final int taskFindAndDrinkWater = 13;
	
	private static final int fleeInterruptTicks = 5;
	
	private EntityPrehistoric entity;
	private Object target;
	private int task;
	private int tickCounter;
	private float radiusDimension;
	private boolean taskDone;
	
	public FossilAIWildIndividualBase(EntityPrehistoric entity) {
		this.entity = entity;
		this.setMutexBits(2);
	}
	
	@Override
	public boolean shouldExecute() {
		return taskDone && !entity.hasOwner() && !entity.isInHerd();
	}
	
	public boolean continueExecuting() {
		return !taskDone && shouldExecute();
	}
	
	public void startExecuting() {
		taskDone = false;
		// ArrayLists to store found entities
		ArrayList<EntityPrehistoric> nearbyPrehistoricEntities = new ArrayList<EntityPrehistoric>();
		ArrayList<EntityPrehistoric> nearbyPrehistoricEntitiesOfSameSpecies = new ArrayList<EntityPrehistoric>();
		ArrayList<EntityLiving> nearbyLivingEntities = new ArrayList<EntityLiving>();
		ArrayList<EntityItem> nearbyItemEntities = new ArrayList<EntityItem>();
		ArrayList<EntityPlayer> nearbyPlayers = new ArrayList<EntityPlayer>();
		Vec3 waterBlock = null;
		boolean checkedForWater = false;
		
		// Sift through all entities
		for(Object tempEntity: entity.worldObj.loadedEntityList) {
			if(tempEntity == entity) {
				continue;
			}
			if(tempEntity instanceof EntityPrehistoric) {
				if(entity.canFindEntity((Entity)tempEntity)) {
					nearbyPrehistoricEntities.add((EntityPrehistoric)tempEntity);
					if(entity.getClass().equals(tempEntity.getClass())) {
						nearbyPrehistoricEntitiesOfSameSpecies.add((EntityPrehistoric)tempEntity);
					}
				}
			} else if(tempEntity instanceof EntityLiving) {
				if(entity.canFindEntity((Entity)tempEntity)) {
					nearbyLivingEntities.add((EntityLiving)tempEntity);
				}
			} else if(tempEntity instanceof EntityItem) {
				if(entity.canFindEntity((Entity)tempEntity)) {
					nearbyItemEntities.add((EntityItem)tempEntity);
				}
			} else if(tempEntity instanceof EntityPlayer) {
				if(entity.canFindEntity((Entity)tempEntity)) {
					nearbyPlayers.add((EntityPlayer)tempEntity);
				}
			}
		}
		
		if(!nearbyPrehistoricEntities.isEmpty()) {
			// Check nearby targets to see if we should flee
			ArrayList<EntityPrehistoric> fleeingTargets = new ArrayList<EntityPrehistoric>();
			for(EntityPrehistoric tempEntity: nearbyPrehistoricEntities) {
				if(entity.getType().shouldRunFromEntity(tempEntity)) {
					fleeingTargets.add(tempEntity);
				}
			}
			
			if(!fleeingTargets.isEmpty()) {
				// Sort based on 
				Collections.sort(fleeingTargets, new Comparator<EntityPrehistoric>() {
					@Override
					public int compare(EntityPrehistoric o1, EntityPrehistoric o2) {
						return (int)(o1.getStrength() - o2.getStrength());
					}
				});
				switch(entity.distanceStatus(fleeingTargets.get(0))) {
				case 1:
					task = this.taskFleeNear;
					break;
				case 2:
					task = this.taskFleeFar;
					break;
				}
				target = fleeingTargets.get(0);
				return;
			}
		}
		
		if(entity.hungerLevel() > 0) {
			ArrayList<TileEntityFeeder> feeders = new ArrayList<TileEntityFeeder>();
			for(Object tempTileEntity: entity.worldObj.loadedTileEntityList) {
				if(tempTileEntity instanceof TileEntityFeeder) {
					if(entity.canFindFeeder((TileEntityFeeder)tempTileEntity)) {
						feeders.add((TileEntityFeeder)tempTileEntity);
					}
				}
			}
			
			if(!feeders.isEmpty()) {
				Collections.sort(feeders, new Comparator<TileEntityFeeder>() {
					@Override
					public int compare(TileEntityFeeder o1, TileEntityFeeder o2) {
						int totalFood1 = 0;
						int totalFood2	= 0;
						if(entity.getType().eatsMeat()) {
							totalFood1 += o1.MeatCurrent;
							totalFood2 += o2.MeatCurrent;
						}
						if(entity.getType().eatsVegetables()) {
							totalFood1 += o1.VegCurrent;
							totalFood2 += o2.VegCurrent;
						}
						return totalFood1 - totalFood2;
					}
				});
				task = this.taskFeedFromFeeder;
				target = feeders.get(0);
				return;
			}
			
			ArrayList<EntityLiving> possibleFood = new ArrayList<EntityLiving>();
			for(EntityPrehistoric food: nearbyPrehistoricEntities) {
				if(entity.getType().willAttack(food)) {
					possibleFood.add(food);
				}
			}
			for(EntityLiving food: nearbyLivingEntities) {
				if(entity.getType().willAttack(food)) {
					possibleFood.add(food);
				}
			}
			
			if(!possibleFood.isEmpty()) {
				Collections.sort(possibleFood, new Comparator<EntityLiving>() {
					@Override
					public int compare(EntityLiving o1, EntityLiving o2) {
						return (int)(entity.distanceToEntity(o1) - entity.distanceToEntity(o2));
					}
				});
				
				task = this.taskTrackAndAttack;
				target = possibleFood.get(0);
				return;
			}
			
			ArrayList<EntityItem> possibleItemFood = new ArrayList<EntityItem>();
			for(EntityItem item: nearbyItemEntities) {
				if(entity.getType().willEat(item.getEntityItem().getItem())) {
					possibleItemFood.add(item);
				}
			}
			
			if(!possibleItemFood.isEmpty()) {
				Collections.sort(possibleItemFood, new Comparator<EntityItem>() {
					@Override
					public int compare(EntityItem o1, EntityItem o2) {
						return (int)(entity.distanceToEntity(o1) - entity.distanceToEntity(o2));
					}
				});
				
				task = this.taskPickupItem;
				target = possibleItemFood.get(0);
				return;
			}
			
			if(entity.getType().eatsBlocks()) {
				ArrayList<Vec3> foodBlocks = new ArrayList<Vec3>();
				for(int i = (int)-entity.getType().getMaxAwarenessRadius(); i <= (int)entity.getType().getMaxAwarenessRadius(); i++) {
					int bound = (int)Math.sqrt(Math.pow(entity.getType().getMaxAwarenessRadius(), 2) - Math.pow(i, 2));
					for(int k = -bound; k <= bound; k++) {
						for(int j = -4; j <= 4; j++) {
							if(entity.getType().willEat(entity.worldObj.getBlock(i, j, k))) {
								foodBlocks.add(Vec3.createVectorHelper(i, j, k));
							} else if(waterBlock == null && entity.worldObj.getBlock(i, j, k).getMaterial().equals(Material.water)) {
								waterBlock = Vec3.createVectorHelper(i, j, k);
								checkedForWater = true;
							}
						}
					}
				}
				
				if(!foodBlocks.isEmpty()) {
					Collections.sort(foodBlocks, new Comparator<Vec3>() {
						@Override
						public int compare(Vec3 o1, Vec3 o2) {
							return (int)(entity.distanceToLocation(o1) - entity.distanceToLocation(o2));
						}
					});
					
					task = this.taskEatBlock;
					target = foodBlocks.get(0);
					return;
				}
			}
			
			if(entity.hungerLevel() > 1) {
				task = this.taskWanderFast;
				setWanderTarget(true);
				return;
			}
		}
		
		if(!nearbyPrehistoricEntitiesOfSameSpecies.isEmpty() && entity.getType().isCanFormHerds()) {
			Collections.sort(nearbyPrehistoricEntitiesOfSameSpecies, new Comparator<EntityPrehistoric>() {
				@Override
				public int compare(EntityPrehistoric o1, EntityPrehistoric o2) {
					return (int)(entity.distanceToEntity(o1) - entity.distanceToEntity(o2));
				}			
			});
			if(nearbyPrehistoricEntitiesOfSameSpecies.get(0).isInHerd()) {
				task = this.taskAskJoinHerd;
			} else {
				task = this.taskFormHerd;
			}
			target = nearbyPrehistoricEntitiesOfSameSpecies.get(0);
			return;
		}
		
		if(!nearbyPlayers.isEmpty()) {
			Collections.sort(nearbyPlayers, new Comparator<EntityPlayer>() {
				@Override
				public int compare(EntityPlayer o1, EntityPlayer o2) {
					return (int)(entity.distanceToEntity(o1) - entity.distanceToEntity(o2));
				}
			});
			if(entity.isAdult()) {
				if(entity.getType().attacksPlayersAsAdult()) {
					task = this.taskTrackAndAttack;
				} else {
					task = this.taskFollow;
				}
				target = nearbyPlayers.get(0);
				return;
			} else if (entity.isChild()) {
				task = this.taskAvoid;
				target = nearbyPlayers.get(0);
				return;
			}
		}
		
		if(!checkedForWater) {
			for(int i = (int)-entity.getType().getMaxAwarenessRadius(); i <= (int)entity.getType().getMaxAwarenessRadius(); i++) {
				int bound = (int)Math.sqrt(Math.pow(entity.getType().getMaxAwarenessRadius(), 2) - Math.pow(i, 2));
				for(int k = -bound; k <= bound; k++) {
					for(int j = -4; j <= 4; j++) {
						if(waterBlock == null && entity.worldObj.getBlock(i, j, k).getMaterial().equals(Material.water)) {
							waterBlock = Vec3.createVectorHelper(i, j, k);
							checkedForWater = true;
							break;
						}
					}
					if(checkedForWater) {
						break;
					}
				}
				if(checkedForWater) {
					break;
				}
			}
		}
		
		if(waterBlock == null) {
			if((new Random()).nextDouble() >= 0.5) {
				task = this.taskWanderSlow;
				setWanderTarget(false);
			} else {
				task = this.taskIdle;
			}
		} else {
			int i = 1;
			while(!entity.worldObj.getBlock((int)waterBlock.xCoord, (int)waterBlock.yCoord + i, (int)waterBlock.zCoord).getMaterial().equals(Material.air)) {
				i++;
			}
			i--;
			waterBlock = waterBlock.addVector(0, i, 0);
			Vec3 targetBlock = Vec3.createVectorHelper(entity.posX, waterBlock.yCoord, entity.posZ);
			MovingObjectPosition hit = entity.worldObj.rayTraceBlocks(waterBlock, targetBlock);
			task = this.taskFindAndDrinkWater;
			target = Vec3.createVectorHelper(hit.blockX, hit.blockY, hit.blockZ);
		}
	}
	
	public void updateTask() {
		if(tickCounter % fleeInterruptTicks == 0 && task != taskFleeNear && task != taskFleeFar) {
			ArrayList<EntityPrehistoric> possibleFleeTargets = new ArrayList<EntityPrehistoric>();
			for(Object tempEntity: entity.worldObj.loadedEntityList) {
				if(tempEntity instanceof EntityPrehistoric && entity.getType().shouldRunFromEntity((EntityPrehistoric)tempEntity) && entity.canFindEntity((Entity)tempEntity)) {
					possibleFleeTargets.add((EntityPrehistoric)tempEntity);
				}
			}
			if(!possibleFleeTargets.isEmpty()) {
				Collections.sort(possibleFleeTargets, new Comparator<EntityPrehistoric>() {
					@Override
					public int compare(EntityPrehistoric o1, EntityPrehistoric o2) {
						return (int)(o1.getStrength() - o2.getStrength());
					}
				});
				switch(entity.distanceStatus(possibleFleeTargets.get(0))) {
				case 1:
					task = this.taskFleeFar;
				case 2:
					task = this.taskFleeNear;
				}
				target = possibleFleeTargets.get(0);
			}
		}
		tickCounter++;
		switch(task) {
		case taskFleeNear:
			fleeFromEntity((Entity)target, true);
			break;
		case taskFleeFar:
			fleeFromEntity((Entity)target, false);
			break;
		case taskAvoid:
			avoidEntity((Entity)target, radiusDimension);
			break;
		case taskWanderFast:
			wander(true);
			break;
		case taskWanderSlow:
			wander(false);
			break;
		case taskFollow:
			followEntity((Entity)target, radiusDimension);
			break;
		case taskIdle:
			idle();
			break;
		case taskTrackAndAttack:
			trackAndAttackEntity((EntityLiving)target);
			break;
		case taskFeedFromFeeder:
			feedFromFeeder((TileEntityFeeder)target);
			break;
		case taskPickupItem:
			pickupItem((EntityItem)target);
			break;
		case taskEatBlock:
			eatBlock((Vec3)target);
			break;
		case taskFormHerd:
			formHerd((EntityPrehistoric)target);
			break;
		case taskAskJoinHerd:
			askToJoinHerd((EntityPrehistoric)target);
			break;
		case taskFindAndDrinkWater:
			findAndDrinkWater((Vec3)target);
			break;
		}
		
		
	}
	
	private void taskDone() {
		taskDone = true;
	}
	
	abstract void setWanderTarget(boolean fast);
	
	abstract void fleeFromEntity(Entity fleeFrom, boolean isClose);
	
	abstract void avoidEntity(Entity avoid, float avoidanceRadius);
	
	abstract void wander(boolean toRunning);
	
	abstract void followEntity(Entity follow, float followDistance);
	
	abstract void idle();
	
	abstract void trackAndAttackEntity(EntityLiving target);
	
	abstract void feedFromFeeder(TileEntityFeeder feeder);
	
	abstract void eatBlock(Vec3 target);
	
	abstract void pickupItem(EntityItem item);
	
	abstract void formHerd(EntityPrehistoric entity);
	
	abstract void askToJoinHerd(EntityPrehistoric entity);
	
	abstract void findAndDrinkWater(Vec3 water);
}
