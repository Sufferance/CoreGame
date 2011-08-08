package ru.o2genum.coregame.game;

import java.util.*;

import ru.o2genum.coregame.framework.*;
import ru.o2genum.coregame.framework.impl.*;
import ru.o2genum.coregame.framework.Input.KeyEvent;

import android.util.*;

public class World
{
	Random random = new Random();
	Game game;
	public List<Dot> dots = new LinkedList<Dot>();
	public Core core = new Core();
	public float offScreenRadius;
	private final int DOTS_COUNT = 10;
	private final float SHIELD_FACTOR = 20.0F;
	private final float ENERGY_FACTOR = 6.0F;
	private float deltaAngle = 0.0F;

	private float time = 0.0F; // in seconds

	public enum GameState {Ready, Running, Paused, GameOver}

	public GameState state = GameState.Ready;

	private float difficulty = 0.04F; // Max 0.1F

	public World(Game game)
	{
		this.game = game;
		Graphics g = game.getGraphics();
		// Construct core
		core.coords = new VectorF((float) g.getWidth() / 2,
			   	(float) g.getHeight() / 2);
		core.shieldRadius = (float) g.getWidth() / 4;
		core.maxRadius = core.shieldRadius * 0.7F;
		core.angle = 0.0F;
		core.health = 1.0F;
		core.shieldEnergy = 0.0F;
		// Set offScreenRadius
		offScreenRadius = (float) Math.hypot((double) g.getWidth() / 2,
				(double) g.getHeight() / 2);
		// Max dot radius (when it's energy is 1.0F)
		Dot.maxRadius = core.maxRadius / 8.0F;
	}
		
	public void renew()
	{
		dots.clear();
		core.health = 1.0F;
		core.shieldEnergy = 0.0F;
		state = GameState.Ready;
		generateStartDots(DOTS_COUNT);
	}
	// Add randomness
	private void generateStartDots(int count)
	{
		for(int i = 0; i < count; i++)
		{
			generateNewDot(true);
		}
	}

	private VectorF generateNewDotCoordsInRandomPlace()
	{
		double angle = random.nextDouble() * 2 * Math.PI;
		VectorF coords = new VectorF((float) Math.cos(angle), 
				(float) Math.sin(angle));
		coords = coords.multiply(core.shieldRadius + (offScreenRadius -
					core.shieldRadius) * random.nextFloat());
		return coords;
	}

	public void update(float deltaTime)
	{
		if(state == GameState.Ready)
			updateReady(deltaTime);
		if(state == GameState.Running)
			updateRunning(deltaTime);
		if(state == GameState.Paused)
			updatePaused(deltaTime);
		if(state == GameState.GameOver)
			updateGameOver(deltaTime);
	}

	private void doInput()
	{
	    float orientAngle = game.getInput().getAzimuth();
		if(game.getInput().isTouchDown())
		{
			double touchX = (double) game.getInput().getTouchX();
			double touchY = (double) game.getInput().getTouchY();
			core.angle = 
				// Y-axis is inverted. See checkCollisionWithShield(...)
				// method
				normAngle(((float) (Math.atan2(-(touchY - core.coords.y), 
						touchX - core.coords.x) / (Math.PI * 2) *
				360.0)) - Core.GAP_ANGLE/2F);
			deltaAngle = core.angle - orientAngle;
		}
		else
		{
			core.angle = normAngle(orientAngle + deltaAngle);
		}
	}

	private void updateReady(float deltaTime)
	{
		if(game.getInput().isTouchDown() || checkMenuUp())
			state = GameState.Running;
	}
	
	private boolean checkMenuUp()
	{
		List<KeyEvent> events = game.getInput().getKeyEvents();
		Iterator<KeyEvent> i = events.iterator();
		while(i.hasNext())
		{
			KeyEvent e = i.next();
			if((e.type == KeyEvent.KEY_UP) && (e.keyCode == 
						android.view.KeyEvent.KEYCODE_MENU))
			{
				return true;
			}
		}
		return false;
	}

	private void updatePaused(float deltaTime)
	{
		if(game.getInput().isTouchDown() || checkMenuUp())
			state = GameState.Running;
	}

	private void updateGameOver(float deltaTime)
	{
		if(game.getInput().isTouchDown() || checkMenuUp())
			renew();
	}

	private void updateRunning(float deltaTime)
	{
		countTime(deltaTime);

		doInput();

		generateNewDots(DOTS_COUNT);

		handleCollisions();
		moveDots(deltaTime);
		decreaseShieldEnergy(deltaTime);
		if(checkMenuUp())
			state = GameState.Paused;
	}

	private void decreaseShieldEnergy(float deltaTime)
	{
		if(core.shieldEnergy > 0.0F)
		{
			core.shieldEnergy -= deltaTime / SHIELD_FACTOR;
			if(core.shieldEnergy < 0.0F)
			core.shieldEnergy = 0.0F;
		}
	}

	private void generateNewDots(int neededCount)
	{
		float rand = random.nextFloat();
		if(neededCount > dots.size())
			generateNewDot(false);
	}

	private void increaseDifficulty()
	{
		difficulty += 0.0001F;
	}

	private void generateNewDot(boolean atStart)
	{
		float linearSpeed = 10.0F * difficulty;
		Dot dot = new Dot();
		if(atStart)
		{
			dot.coords = generateNewDotCoordsInRandomPlace();
		}
		else
		{
			dot.coords = generateNewDotAtOffScreenRadius();
		}
		VectorF speed = new VectorF(
				linearSpeed * (-dot.coords.x / dot.coords.length()),
			   linearSpeed * (-dot.coords.y / dot.coords.length()));
		dot.speed = speed;
		dot.coords = dot.coords.add(core.coords);
		dot.energy = random.nextFloat();
		if(dot.energy <= 0.3F)
			dot.energy = 0.3F;
		float typeRand = random.nextFloat();
		Dot.Type type;
		if (typeRand >= 0.9)
			type = Dot.Type.Shield;
		else if (typeRand >= 0.8)
			type = Dot.Type.Health;
		else
			type = Dot.Type.Enemy;
		dot.type = type;
		dots.add(dot);
	}

	private void countTime(float deltaTime)
	{
		time += deltaTime;
	}

	public String getTime()
	{
		int seconds = (int) time;
		int minutes = seconds / 60;
		seconds %= 60;
		String result = "";
		if(minutes > 0)
			result += minutes + ":";
		result += String.format("%02d", seconds);
		return result;
	}

	private VectorF generateNewDotAtOffScreenRadius()
	{
		float angle = random.nextFloat() * ((float)(2 * Math.PI));
		VectorF coords =
		   	new VectorF(offScreenRadius * ((float) Math.cos(angle)),
					offScreenRadius * ((float) Math.sin(angle)));
		return coords;
	}
	private void moveDots(float deltaTime)
	{
		Iterator<Dot> iterator = dots.iterator();
		while(iterator.hasNext())
		{
			Dot dot = iterator.next();
			dot.coords = dot.coords.add(dot.speed.multiply(deltaTime * 100.0F));
		}
	}

	private void handleCollisions()
	{
		Iterator<Dot> iterator = dots.iterator();
		while(iterator.hasNext())
		{
			handleCollision(iterator.next(), iterator);
		}
	}

	private void handleCollision(Dot dot, Iterator<Dot> iterator)
	{
		float lengthToCoreCenter = dot.coords.substract(core.coords).length(); 
		if(Math.abs(lengthToCoreCenter - 
					core.shieldRadius) <= dot.maxRadius * dot.energy +
				Core.SHIELD_WIDTH)
			checkCollisionWithShield(dot, iterator);
		else if (lengthToCoreCenter - core.maxRadius * core.health <=
			   	dot.maxRadius * dot.energy)
			handleCollisionWithCore(dot, iterator);
	}

	private void checkCollisionWithShield(Dot dot, Iterator<Dot> iterator)
	{
		// I normalize (move into (0; 360) interval) angles
		// in some places. Don't know if it's needed.
		if(core.shieldEnergy > 0.0F)
		{
			iterator.remove();
			game.getVibration().vibrate(30);
		}
		else
		{
		VectorF v = dot.coords.substract(core.coords);
		// Pay attention at -v.y! Y-axis is inverted, 
		// because it points downwards.
		float dotAngle = (float) Math.atan2((double) - v.y, (double) v.x);
		dotAngle = dotAngle / (((float) Math.PI) * 2.0F) * 360.0F;
		dotAngle = normAngle(dotAngle);
		Log.d("LOL", "core.angle = " + core.angle + "; dotAngle = " + dotAngle);
		// For example, dotAngle = 3, and core.angle = 365
		// We need to solve this somehow:
		core.angle = normAngle(core.angle);
		while(dotAngle < core.angle)
		{
			dotAngle += 360.0F;
		}
		// OK, and check if dotAngle is within the gap
		if(!((dotAngle > core.angle) &&
				   	(dotAngle < core.angle + core.GAP_ANGLE)))
		{
			iterator.remove();
			game.getVibration().vibrate(30);
		}
		}
	}

	private float normAngle(float angle)
	{
		float angle2 = angle;
		while(angle2 < 0.0F)
			angle2 += 360.0F;

		while(angle2 > 360.0F)
			angle2 -= 360.0F;

		return angle2;
	}

	private void handleCollisionWithCore(Dot dot, Iterator<Dot> iterator)
	{
		if(dot.type == Dot.Type.Enemy)
		{
		core.health -= dot.energy / ENERGY_FACTOR;
		if(core.health < 0.0F)
		{
			state = GameState.GameOver;
			core.health = 0.0F;
		}
		}
		else if (dot.type == Dot.Type.Health)
		{
		core.health += dot.energy / ENERGY_FACTOR;
		if(core.health > 1.0F)	
		{
			core.health = 1.0F;
		}
		}
		else if(dot.type == Dot.Type.Shield)
		{
			core.shieldEnergy += dot.energy;
			if(core.shieldEnergy > 1.0F)
				core.shieldEnergy = 1.0F;
		}
		iterator.remove();
		game.getVibration().vibrate(30);
	}

}