package org.jsoupstream.selector;

/**
 *  Keeps track of level and sequence where the match occured. A singlr integer is used to represent level and sequence.
 */
public class LevelsMatchedArray
{
    private final static int FACTOR = 10000;
    private final static int INCREMENT = 4;

    private int max = INCREMENT; // upper bound of element in array.
    private int current = 0; // current position in array - for searching only.
    private int[] levels;

    public LevelsMatchedArray()
    {
        levels = new int[max];
    }

    public void add( int level, int sequence )
    {
        for ( int i = 0; i < max; i++ )
        {
            if ( levels[i] == 0 )
            {
                levels[i] = ( level * FACTOR ) + sequence;
                return;
            }
        }

        // grow the array to accomodate more level/sequence pairs
        int newMax = max + INCREMENT;
        int temp[] = new int[newMax];
        for ( int i = 0; i < max; i++ )
        {
            temp[i] = levels[i];
        }
        temp[max] = ( level * FACTOR ) + sequence;
        levels = temp;
        max = newMax;
    }

    public void remove( int level )
    {
        for ( int i = 0; i < max; i++ )
        {
            if ( ( levels[i] / FACTOR ) > level )
            {
                levels[i] = 0;
            }
        }
    }

    public void clear( )
    {
        for ( int i = 0; i < max; i++ )
        {
            levels[i] = 0;
        }
    }

    public boolean hasMatches( )
    {
        for ( int i = 0; i < max; i++ )
        {
            if ( levels[i] != 0 )
            {
                return true;
            }
        }

        return false;
    }

    public void resetIndex( )
    {
        current = 0;
    }

    public int getNext( int level )
    {
        while ( current < max )
        {
            if ( ( levels[current] / FACTOR ) == level )
            {
                return levels[current++];
            }
            current++;
        }

        return -1;
    }

    public int getNext( )
    {
        while ( current < max )
        {
            if ( levels[current] > 0 )
            {
                return levels[current++];
            }
            current++;
        }

        return -1;
    }

    public static int getLevel( int num )
    {
        return ( num / FACTOR );
    }

    public static int getSequence( int num )
    {
        return ( num % FACTOR );
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append( "{" );
        for ( int i = 0; i < max; i++ )
        {
            if ( levels[i] != 0 )
            {
                sb.append( "<" );
                sb.append( (levels[i] / FACTOR ) );
                sb.append( "," );
                sb.append( (levels[i] % FACTOR ) );
                sb.append( ">" );
            }
        }
        sb.append( "}" );
        return sb.toString();
    }
}
