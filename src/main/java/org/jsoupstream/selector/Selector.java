package org.jsoupstream.selector;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import org.jsoupstream.HtmlToken;
import org.jsoupstream.SymbolTable;

/**
 * Represents a selector with all Components (combination of Combinator, Element, and Attribute selectors)
 * and associated Action(s)
 */
public class Selector
{
    private final int line; // used for messages
    private final ArrayList<Component> components = new ArrayList<Component>();
    private final ArrayList<Action> actions = new ArrayList<Action>();

    private boolean done = false; // short circuit to optimize performance
    private int start = 1; // when to start executing actions
    private int count = 0; // how many times to executing actions ( 0 means unlimited )
    private int matches = 0; // how many times selector matched
    private int executes = 0; // how many times selector was exeuted

    private ArrayDeque<Integer> levelsMatched = new ArrayDeque<Integer>();

    public Selector(int line)
    {
        this.line = line;
    }

    // creates and adds a new component to the end of the list
    public Component addComponent(String tag, Component.CombinatorType type)
    {
        Component component = new Component(tag, type);
        this.components.add( component );
        return component;
    }

    public Component getComponent(int index)
    {
        if ( index >= 0 && index < components.size() )
        {
            return this.components.get( index );
        }
        return null;
    }

    public List<Component> getComponents()
    {
        return this.components;
    }

    public void addAction(Action action)
    {
        this.actions.add( action );
    }

    public void setDone(boolean done)
    {
        this.done = done;
    }

    public void setStart(int start)
    {
        this.start = start;
    }

    public void setCount(int count)
    {
        this.count = count;
    }

    public int getStart()
    {
        return start;
    }

    public int getCount()
    {
        return count;
    }

    public boolean isExpired()
    {
        if ( done )
        {
            return true;
        }
        else if ( count == 0 )
        {
            return false;
        }
        else
        {
            return ( executes >= count );
        }
    }

    public boolean check( HtmlToken stackToken, List<HtmlToken> tokenQueue, int level, int sequence )
    {
        boolean anyMatched = false;
        boolean matched = false;
        int depthMatched = 0;
        int lastLevel = 0;
        Component lastComponent = null;
        Component component = null;
        Component nextComponent = null;
 
        // find how deep we have already matched
        Iterator<Component> it = components.iterator();
        if ( it.hasNext() )
        {
            nextComponent = it.next();
        }

        while ( nextComponent != null )
        {
            component = nextComponent;
            if ( it.hasNext() )
            {
                nextComponent = it.next();
            }
            else
            {
                nextComponent = null;
            }

            if ( depthMatched == 0 )
            {
                // root component - no previous level/sequence
                matched = component.matches( tokenQueue, 0, 0, level, sequence );
            }
            else
            {
                lastComponent.resetLevelIndex();
                lastLevel = lastComponent.getNextMatchedLevel();
                matched = false;
                while ( LevelsMatchedArray.getLevel( lastLevel ) > 0 )
                {
                    matched = component.matches( tokenQueue, LevelsMatchedArray.getLevel( lastLevel ), LevelsMatchedArray.getSequence( lastLevel ), level, sequence );
                    if ( matched )
                    {
                        break;
                    }

                    lastLevel = lastComponent.getNextMatchedLevel();
                }
            }

            if ( ! matched )
            {
                if ( stackToken == null || stackToken.getSymbolType() != SymbolTable.Type.VOID_ELEMENT )
                {
                    if ( nextComponent != null )
                    {
                        component.clearLevelMatched( ( level + nextComponent.getLevelAdjustment() ), false );
                    }
                    else
                    {
                        component.clearLevelMatched( level, false );
                    }
                }
            }
            if ( ! component.hasLevelsMatched() )
            {
                break;
            }
            lastComponent = component;
            depthMatched++;
        }
        
        // if we matched all components, return true
        if ( matched && depthMatched == components.size() )
        {
            levelsMatched.push( new Integer( level ) );
            matches++;
            return true;
        }

        return false;
    }

    public void clearLevelMatched( int level, boolean implied )
    {
        for ( Component component : components )
        {
            component.clearLevelMatched( level, implied );
        }
    }

    public void reset( )
    {
        matches = 0;
        executes = 0;
        done = false;
        for ( Component component : components )
        {
            component.reset( );
        }
    }

    public void executeActions( List<HtmlToken> tokenQueue, Set<Selector> removeSet, int level, boolean implied )
    {
        if ( levelsMatched.size() == 0 )
        {
            return;
        }

        int levelMatch = levelsMatched.peek();
        if ( level != levelMatch )
        {
            return;
        }
        levelsMatched.pop();

        if ( matches < start || isExpired() )
        {
            return;
        }

        for (Action action : actions)
        {
            // all passed parameters are strings
            if ( ! action.execute( this, tokenQueue ) )
            {
                break;
            }
        }

        if ( removeSet != null && levelsMatched.size() == 0 )
        {
            removeSet.add( this );
        }

        this.clearLevelMatched( level, implied );

        executes++;
        return;
    }


    public int getLevelMatched()
    {
        return levelsMatched.peek();
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer( "Selector "+line+": " );
        for ( Component component : components )
        {
            sb.append( component );
        }
        sb.append( " {" );
        for ( Action action : actions )
        {
            sb.append( action );
            sb.append( ";" );
        }
        sb.append( "}" );

        return sb.toString();
    }
}
