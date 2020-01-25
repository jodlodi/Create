package com.simibubi.create.modules.logistics.block.inventories;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTileEntities;
import com.simibubi.create.foundation.block.SyncedTileEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.AxisDirection;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class FlexcrateTileEntity extends SyncedTileEntity implements INamedContainerProvider {

	public class Inv extends ItemStackHandler {
		public Inv() {
			super(32);
		}

		@Override
		public int getSlotLimit(int slot) {
			if (slot < allowedAmount / 64)
				return super.getSlotLimit(slot);
			else if (slot == allowedAmount / 64)
				return allowedAmount % 64;
			return 0;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if (slot > allowedAmount / 64)
				return false;
			return super.isItemValid(slot, stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			super.onContentsChanged(slot);
			markDirty();

			itemCount = 0;
			for (int i = 0; i < getSlots(); i++) {
				itemCount += getStackInSlot(i).getCount();
			}
		}
	}

	public Inv inventory;
	public int allowedAmount;
	public int itemCount;
	protected LazyOptional<IItemHandler> invHandler;

	public FlexcrateTileEntity() {
		this(AllTileEntities.FLEXCRATE.type);
	}

	public FlexcrateTileEntity(TileEntityType<?> type) {
		super(type);
		allowedAmount = 512;
		itemCount = 10;
		inventory = new Inv();
		invHandler = LazyOptional.of(() -> inventory);
	}

	@Override
	public Container createMenu(int id, PlayerInventory inventory, PlayerEntity player) {
		return new FlexcrateContainer(id, inventory, this);
	}

	public boolean isDoubleCrate() {
		return getBlockState().get(FlexcrateBlock.DOUBLE);
	}

	public FlexcrateTileEntity getMainCrate() {
		if (isDoubleCrate() && getFacing().getAxisDirection() == AxisDirection.NEGATIVE)
			return getOtherCrate();
		return this;
	}

	public FlexcrateTileEntity getOtherCrate() {
		if (!AllBlocks.FLEXCRATE.typeOf(getBlockState()))
			return null;
		TileEntity tileEntity = world.getTileEntity(pos.offset(getFacing()));
		if (tileEntity instanceof FlexcrateTileEntity)
			return (FlexcrateTileEntity) tileEntity;
		return null;
	}

	public Direction getFacing() {
		return getBlockState().get(FlexcrateBlock.FACING);
	}

	public void onDestroyed() {
		FlexcrateTileEntity other = getOtherCrate();
		if (other == null) {
			for (int slot = 0; slot < inventory.getSlots(); slot++)
				drop(slot);
			return;
		}

		FlexcrateTileEntity main = getMainCrate();
		if (this == main) {
			for (int slot = 0; slot < inventory.getSlots(); slot++) {
				other.inventory.setStackInSlot(slot, inventory.getStackInSlot(slot));
				inventory.setStackInSlot(slot, ItemStack.EMPTY);
			}
			other.allowedAmount = Math.min(1024, allowedAmount);
		}

		for (int slot = 16; slot < other.inventory.getSlots(); slot++)
			other.drop(slot);

		other.invHandler.invalidate();
		other.invHandler = LazyOptional.of(() -> other.inventory);
	}

	private void drop(int slot) {
		InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), inventory.getStackInSlot(slot));
	}

	@Override
	public CompoundNBT write(CompoundNBT compound) {
		if (getMainCrate() == this) {
			compound.putBoolean("Main", true);
			compound.putInt("AllowedAmount", allowedAmount);
			compound.put("Inventory", inventory.serializeNBT());
		}
		return super.write(compound);
	}

	@Override
	public void read(CompoundNBT compound) {
		if (compound.contains("Main")) {
			allowedAmount = compound.getInt("AllowedAmount");
			inventory.deserializeNBT(compound.getCompound("Inventory"));
		}
		super.read(compound);
	}

	@Override
	public ITextComponent getDisplayName() {
		return new StringTextComponent(getType().getRegistryName().toString());
	}

	public void sendToContainer(PacketBuffer buffer) {
		buffer.writeBlockPos(getPos());
		buffer.writeCompoundTag(getUpdateTag());
	}

	@Override
	public void remove() {
		super.remove();
		invHandler.invalidate();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
			return getMainCrate().invHandler.cast();
		return super.getCapability(capability, facing);
	}

}
