package Photon.ToolKit;

import Photon.Information.BreakingNews;
import Photon.Information.PlayAI;
import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Array;
import arc.struct.Queue;
import arc.util.Interval;
import arc.util.Structs;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.entities.traits.BuilderTrait;
import mindustry.entities.traits.BuilderTrait.BuildRequest;
import mindustry.entities.type.Player;
import mindustry.entities.type.SolidEntity;
import mindustry.entities.type.TileEntity;
import mindustry.entities.type.Unit;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.input.Binding;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.ItemType;
import mindustry.type.Mech;
import mindustry.world.Tile;
import mindustry.world.blocks.sandbox.ItemSource.ItemSourceEntity;
import mindustry.world.modules.ItemModule;

import java.lang.reflect.Field;
import java.util.HashMap;

import static arc.Core.camera;
import static mindustry.Vars.*;

/* Auto mode */
public class Auto {
    public static final int votekickWaitTimer = 0;


    public boolean enabled = true;
    public boolean movementActive = false;
    public Mode mode;
    public boolean persist = false;
    public float targetDistance = 0.0f;
    public boolean freecam = false;

    public Tile targetTile;
    public Unit targetEntity;
    public Tile targetItemSource;
    public Tile autoDumpTarget;
    public Tile autoPickupTarget;
    public static final int itemTransferTimer = 1;
    public Vec2 cameraTarget = new Vec2();

    public float targetEntityLastRotation;

    public Interval timer = new Interval(3);
    public static final int requestItemTimer = 2;
    public Item autoPickupTargetItem;

    public boolean setAutoPickupTarget(Tile tile, Item item) {
        if (tile == null) {
            autoPickupTarget = null;
            autoPickupTargetItem = null;
            return true;
        }
        if (!tile.block().hasItems || !tile.interactable(player.getTeam())) return false;
        if(!tile.block().hasItems(item, tile))return false;
        autoPickupTarget = tile;
        autoPickupTargetItem = item;
        return true;
    }

    public Vec2 movement;
    public Vec2 velocity;

    public boolean movementControlled = false;
    public boolean shootControlled = false;
    public boolean overrideCamera = false;

    public boolean wasAutoShooting = false;

    public Field itemSourceEntityOutputItemField;
    public Item ore;
    public HashMap<Mech, Array<Item>> minennable = new HashMap<>();
    public TileEntity core;
    public Array<Tile> drainable = new Array<>();
    public Queue<drainer> drainQueue = new Queue<>();
    public Tile temp;
    public int count = 0;
    public int count2 = 0;
    public Auto() throws IllegalAccessException {
        Events.on(EventType.WorldLoadEvent.class, ()-> {
            minennable.clear();
            player.getClosestCore();
            drainable.clear();
        });
        Events.on(OnReachedDestination.class, t->{
            if(mode.equals(Mode.mine))
                if(player.item().amount <= player.getItemCapacity()  && player.getMineTile() == null )
                    tryBeginMine(t.tile);

        });
        try {
            Class<Player> playerClass = Player.class;
            Field playerMovementField = playerClass.getDeclaredField("movement");
            playerMovementField.setAccessible(true);
            movement = (Vec2) playerMovementField.get(player);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("reflective access failed on Player.movement");
        }
        try {
            Class<SolidEntity> solidEntityClass = SolidEntity.class;
            Field playerVelocityField = solidEntityClass.getDeclaredField("velocity");
            playerVelocityField.setAccessible(true);
            velocity = (Vec2) playerVelocityField.get(player);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("reflective access failed on SolidEntity.velocity");
        }

        try {
            Class<ItemSourceEntity> itemSourceEntityClass = ItemSourceEntity.class;
            itemSourceEntityOutputItemField = itemSourceEntityClass.getDeclaredField("outputItem");
            itemSourceEntityOutputItemField.setAccessible(true);
        } catch (NoSuchFieldException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
    }


    public void drainResource(){
        /*
        drainable.clear();
        drainQueue.clear();
        autoPickupTargetItem = null;
        autoPickupTarget = null;
        iterateAllTiles(s ->{
                if(s.block().hasItems && s.interactable(player.getTeam()) && s.entity != null)
                    drainable.add(s);
        });
        for(Tile tile : drainable){
            for(Item item : content.items()) {
                if(tile.block().hasItems(item, tile))
                    drainQueue.addLast(new drainer(tile, item, calculateHowManyTriesNeeded(tile.entity.items, item)));

            }drainable.remove(tile);
        }
       */
        autoPickupTarget = player.getClosestCore().tile;
        autoPickupTargetItem = player.getClosestCore().items.last();
        mode = Mode.drainResource;
    }

    public void goMine(){
        if(mode != Mode.mine) {
            if(core == null)
                core = player.getClosestCore();
            mode = Mode.mine;
            movementActive = true;
            autoDumpTarget = ( core.tile);
        }else {
            mode = null;
            movementActive = false;
            autoDumpTarget = null;
        }
        persist = false;
        targetTile = null;
    }

    public void gotoTile(Tile tile, float distance) {
        movementActive = true;
        mode = Mode.GotoTile;
        targetTile = tile;
        targetDistance = distance;
        persist = false;
    }

    public void gotoEntity(Unit unit, float distance, boolean follow) {
        movementActive = true;
        mode = Mode.GotoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = follow;
    }

    public void assistEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.AssistEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public void undoEntity(Unit unit, float distance) {
        movementActive = true;
        mode = Mode.UndoEntity;
        targetEntity = unit;
        targetDistance = distance;
        persist = true;
    }

    public boolean manageItemSource(Tile tile) {
        if (tile == null) {
            targetItemSource = null;
            return true;
        }
        if (tile.block() != Blocks.itemSource) return false;
        targetItemSource = tile;
        return true;
    }

    public boolean setAutoDumpTransferTarget(Tile tile) {
        if (tile == null) {
            autoDumpTarget = null;
            return true;
        }
        if (!tile.block().hasItems || !tile.interactable(player.getTeam())) return false;
        autoDumpTarget = tile;
        return true;
    }

    public void updateDrain(){
        if(mode == Mode.drainResource) {
            autoPickupTargetItem = player.getClosestCore().items.first();
            if (player.item().amount > 0)
                player.clearItem();
        }
    }
    void getDrainQueue(){
        drainer d = drainQueue.first();
        autoPickupTarget = d.tile;
        autoPickupTargetItem = d.item;
    }
    public void setFreecam(boolean enable) {
        setFreecam(enable, player.x, player.y);
    }

    public void setFreecam(boolean enable, float x, float y) {
        if (enable) {
            cameraTarget.set(x, y);
            freecam = true;
        } else {
            freecam = false;
        }
    }

    /** whether default camera handling should be disabled */
    public boolean cameraOverride() {
        return overrideCamera || freecam;
    }

    /** whether default movement handling should be disabled */
    public boolean movementOverride() {
        return freecam;
    }

    public void update() {
        if (!enabled) return;

        updateItemSourceTracking();
        updateAutoDump();
        updateAutoPickup();
        updateMovement();
        updateCamera();
        updateControls();
        updateMine();
        updateDrain();
    }

    public void updateAutoDump() {
        Tile tile = autoDumpTarget;
        if (tile == null || !tile.block().hasItems || !tile.interactable(player.getTeam())) {
            // tile doesn't accept items, reset the thing
            autoDumpTarget = null;
            return;
        }
        ItemStack stack = player.item();
        // if (!timer.get(itemTransferTimer, 50)) return;
        if (stack.amount > 0 &&
                tile.block().acceptStack(stack.item, stack.amount, tile, player) > 0 &&
                !player.isTransferring) {
            Call.transferInventory(player, tile);
        }
    }

    public void updateMine(){
        if(mode == null || !mode.equals(Mode.mine))
            return;
        if(autoDumpTarget == null) {
            mode = null;
            PlayAI.Todo = PlayAI.Mode.AFK;
            player.setMineTile(null);
            return;
        }
        if(player.getMineTile() != null){
            if(ore != Structs.findMin(minennable.get(player.mech), indexer::hasOre, (a, b) -> -Integer.compare(core.items.get(a), core.items.get(b))) || player.item().amount >= player.getItemCapacity()){
                player.setMineTile(null);
            }
        }
        if(targetTile == null && player.getMineTile() == null) {
            if(!minennable.containsKey(player.mech)) {
                Array<Item> miserable = new Array<>();
                for (Item item : BreakingNews.allMinable) {
                    if (player.canMine(item) && indexer.hasOre(item))
                        miserable.add(item);
                }
                minennable.put(player.mech, miserable);
            }
            player.clearItem();
            if (core == null)
                core = player.getClosestCore();
            ore = Structs.findMin(minennable.get(player.mech), indexer::hasOre, (a, b) -> -Integer.compare(core.items.get(a), core.items.get(b)));
            targetTile = indexer.findClosestOre(player.x, player.y, ore);
            movementActive = true;
            targetDistance = player.getMiningRange() - 5f;
        }

    }

    int calculateHowManyTriesNeeded(ItemModule im, Item item){
        if(item == null || im == null)return 0;
        if(im.total() == 0 || !im.has(item))return 0;
        return im.get(item) / player.mech.itemCapacity;
    }
    boolean canMine(Tile tile){
        return !Core.scene.hasMouse()
                && tile.drop() != null && tile.drop().hardness <= player.mech.drillPower
                && !(tile.floor().playerUnmineable && tile.overlay().itemDrop == null)
                && player.acceptsItem(tile.drop())
                && tile.block() == Blocks.air && player.dst(tile.worldx(), tile.worldy()) <= Player.mineDistance;
    }
    void tryBeginMine(Tile tile){
        if(canMine(tile)){
            //if a block is clicked twice, reset it
            player.setMineTile(player.getMineTile() == tile ? null : tile);
        }
    }
    void kidnapItem(Tile tile, Item item){
        ItemStack stack = player.item();
        if (stack.amount > 0 && stack.item != item) return;
        int amount = player.mech.itemCapacity - stack.amount;
        amount = Math.min(amount, tile.entity.items.get(item));
        if (amount == 0) return;
        // if (!timer.get(requestItemTimer, 50)) return;
        Call.requestItem(player, tile, item, amount);

    }
    public void iterateAllTiles(Cons<Tile> fn) {
        Tile[][] tiles = world.getTiles();
        for (Tile[] tile : tiles) {
            for (Tile value : tile) {
                fn.get(value);
            }
        }
    }

    public void updateAutoPickup() {
        Tile tile = autoPickupTarget;
        Item item = autoPickupTargetItem;
        if (tile == null || !tile.block().hasItems || !tile.interactable(player.getTeam()) || item == null) {
            // tile doesn't accept items, reset the thing
            autoPickupTarget = null;
            autoPickupTargetItem = null;
            if(mode == Mode.drainResource)
                mode = null;
            return;
        }

        ItemStack stack = player.item();
        if (stack.amount > 0 && stack.item != item) return;
        int amount = player.mech.itemCapacity - stack.amount;
        amount = Math.min(amount, tile.entity.items.get(item));
        if (amount == 0) return;
        // if (!timer.get(requestItemTimer, 50)) return;
        Call.requestItem(player, tile, item, amount);
    }

    public void updateMovement() {
        if (!movementActive) return;
        float speed = !player.mech.flying
                ? player.mech.boostSpeed
                : player.mech.speed;

        float targetX;
        float targetY;
        switch (mode) {
            case GotoTile:
                if (targetTile == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetTile.getX();
                targetY = targetTile.getY();
                break;
            case GotoEntity:
            case AssistEntity:
            case UndoEntity:
                if (targetEntity == null) {
                    movementActive = false;
                    return;
                }
                targetX = targetEntity.x;
                targetY = targetEntity.y;
                break;
            default:
                throw new RuntimeException("invalid mode");
        }

        movementControlled = false;
        if (player.dst(targetX, targetY) < targetDistance) {
            Events.fire(new OnReachedDestination(targetTile));
            movement.setZero();
            if (!persist) {
                player.isBoosting = false;
                cancelMovement();
            }
        } else if (!Core.input.keyDown(Binding.suspend_movement)) { // allow suspend of movement by holding down key
            player.isBoosting = true;
            movement.set(
                    (targetX - player.x) / Time.delta(),
                    (targetY - player.y) / Time.delta()
            ).limit(speed);
            movement.setAngle(Mathf.slerp(movement.angle(), velocity.angle(), 0.05f));
            velocity.add(movement.scl(Time.delta()));
            movementControlled = true;
        }

        shootControlled = false;
        assistBlock:
        if (mode == Mode.AssistEntity) {
            if (targetEntity instanceof Player) {
                Player targetPlayer = (Player)targetEntity;
                // crappy is shooting logic
                if (!targetPlayer.getTimer().check(targetPlayer.getShootTimer(false), targetPlayer.getWeapon().reload * 1.25f)) {
                    player.buildQueue().clear();
                    player.isBuilding = false;
                    player.isShooting = true;
                    wasAutoShooting = true;
                    shootControlled = true;

                    player.rotation = Mathf.slerpDelta(player.rotation, targetEntityLastRotation, 0.1f * player.mech.getRotationAlpha(player));
                    float rotationDeg = targetEntityLastRotation * Mathf.degreesToRadians;
                    player.pointerX = player.getX() + 200 * Mathf.cos(rotationDeg);
                    player.pointerY = player.getY() + 200 * Mathf.sin(rotationDeg);
                    break assistBlock;
                } else if (wasAutoShooting) {
                    player.isShooting = false;
                    wasAutoShooting = false;
                }
            }
            if (targetEntity instanceof BuilderTrait) {
                BuilderTrait targetBuildEntity = (BuilderTrait)targetEntity;
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    Queue<BuildRequest> buildQueue = player.buildQueue();
                    buildQueue.clear();
                    buildQueue.addFirst(targetRequest);
                    player.isBuilding = true;
                    player.isShooting = false;
                    break assistBlock;
                }
            }
        } else if (mode == Mode.UndoEntity) {
            if (targetEntity instanceof BuilderTrait) {
                BuilderTrait targetBuildEntity = (BuilderTrait) targetEntity;
                // TODO: handle configures
                BuildRequest targetRequest = targetBuildEntity.buildRequest();
                if (targetRequest != null) {
                    BuildRequest undo;
                    if (targetRequest.breaking) {
                        Tile target = world.tile(targetRequest.x, targetRequest.y);
                        undo = new BuildRequest(targetRequest.x, targetRequest.y, target.rotation(), target.block());
                    } else undo = new BuildRequest(targetRequest.x, targetRequest.y);
                    player.buildQueue().addLast(undo);
                    player.isBuilding = true;
                }
            }
        }

        if(velocity.len() <= 0.2f && player.mech.flying){
            player.rotation += Mathf.sin(Time.time() + player.id * 99, 10f, 1f);
        }else if(player.target == null){
            player.rotation = Mathf.slerpDelta(player.rotation, velocity.angle(), velocity.len() / 10f);
        }
        player.updateVelocityStatus();
    }

    public void updateItemSourceTracking() {
        if (targetItemSource == null) return;
        if (targetItemSource.block() != Blocks.itemSource) {
            griefWarnings.sendMessage("[gray]Notice[] Item source " + griefWarnings.formatTile(targetItemSource) + " gone");
            targetItemSource = null;
            return;
        }
        TileEntity core = player.getClosestCore();
        if (core == null) return;
        ItemModule items = core.items;

        Item least = null;
        int count = Integer.MAX_VALUE;
        for (int i = 0; i < content.items().size; i++) {
            Item currentItem = content.item(i);
            if (currentItem.type != ItemType.material) continue;
            int currentCount = items.get(currentItem);
            if (currentCount < count) {
                least = currentItem;
                count = currentCount;
            }
        }
        ItemSourceEntity entity = targetItemSource.ent();
        Item currentConfigured;
        try {
            currentConfigured = (Item)itemSourceEntityOutputItemField.get(entity);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("reflective access failed on ItemSourceEntity.outputItem");
        }
        if (least != null && least != currentConfigured) targetItemSource.configure(least.id);
    }

    public enum Mode { GotoTile, GotoEntity, AssistEntity, UndoEntity, mine, drainResource }

    /** Custom camera handling, if enabled */
    public void updateCamera() {
        if (!cameraOverride()) return;
        if (freecam && !ui.chatfrag.shown()) {
            float camSpeed = !Core.input.keyDown(Binding.dash) ? 10f : 25f;
            cameraTarget.add(Tmp.v1.setZero().add(Core.input.axis(Binding.move_x), Core.input.axis(Binding.move_y)).nor().scl(Time.delta() * camSpeed));

            if(Core.input.keyDown(Binding.mouse_move)){
                cameraTarget.x += Mathf.clamp((Core.input.mouseX() - Core.graphics.getWidth() / 2f) * 0.005f, -1, 1) * camSpeed;
                cameraTarget.y += Mathf.clamp((Core.input.mouseY() - Core.graphics.getHeight() / 2f) * 0.005f, -1, 1) * camSpeed;
            }
        }

        camera.position.lerpDelta(cameraTarget, 0.08f);
    }

    public void updateControls() {
        if (Core.scene.hasKeyboard()) return;
        if (Core.input.keyTap(Binding.freecam)) setFreecam(!freecam);
    }

    /** Perform necessary cleanup after stopping */
    public void cancelMovement() {
        movementActive = false;
        persist = false;
        targetTile = null;
        targetEntity = null;
        movementControlled = false;
        shootControlled = false;
        wasAutoShooting = false;
    }

    public void reset() {
        cancelMovement();
        targetItemSource = null;
        autoDumpTarget = null;
        autoPickupTarget = null;
        overrideCamera = false;
    }

    public void handlePlayerShoot(Player target, float offsetX, float offsetY, float rotation) {
        if (target == targetEntity) targetEntityLastRotation = rotation;
    }

    public boolean interceptMessage(String message, String sender, Player playersender) {
        // message is annoying
        if (message.startsWith("[scarlet]You must wait ") && sender == null) {
            return !timer.get(votekickWaitTimer, 90);
        }
        return false;
    }

    public void votekick(String identifier) {
        Player p = griefWarnings.commandHandler.getPlayer(identifier);
        if (p == null) return;
        Call.sendChatMessage("/votekick " + p.name);
    }

      static class OnReachedDestination{
        public final Tile tile;
        public OnReachedDestination(Tile tile){

            this.tile = tile;
        }
    }
    static class drainer{
        public final Tile tile;
        public final Item item;
        public final int trys;

        drainer(Tile tile, Item item, int trys) {
            this.tile = tile;
            this.item = item;
            this.trys = trys;
        }
    }
}
