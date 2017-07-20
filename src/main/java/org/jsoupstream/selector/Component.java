package org.jsoupstream.selector;

import org.jsoupstream.HtmlToken;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *  Represents a single component of a selector. (i.e. Combinator TAG[attr]:pseudo) 
 */
public class Component
{

    // The Combinator type
    public static enum CombinatorType
    {
        ROOT,       // top level - no predecessor
        DESCENDENT, // space
        CHILD,      // >
        ADJACENT,   // +
        SIBLING     // ~
    }

    private final CombinatorType combinator; // relation to preceeding Component in Selector
    private final String tagSelector; // tag name or '*' (universal selector)

    // Pseudo selectors:
    // first-child == 0n+1, odd = 2n+1, even = 2n+0, 5th child = 0n+5
    private int nthChildA = 1; // the a in an+b
    private int nthChildB = 0; // the b in an+b

    // Attribute selectors:
    private ArrayList<AttributeSelector> attributes = new ArrayList<AttributeSelector>();
 
    // All level/sequence combinations this component has matched on
    private LevelsMatchedArray levelsMatched = new LevelsMatchedArray();

    public Component(String tag, CombinatorType type)
    {
        this.tagSelector = tag;
        this.combinator = type;
    }

    public void addAttribute( AttributeSelector attribute )
    {
        this.attributes.add( attribute );
    }

    public void setNthChild( int a, int b )
    {
        nthChildA = a;
        nthChildB = b;
    }

    // Called with a single starting or self closing element
    public boolean matches( List<HtmlToken> tokenQueue, int lastLevel, int lastSequence, int level, int sequence )
    {
        Iterator<HtmlToken> it = tokenQueue.iterator();
        boolean state = true;
        HtmlToken token;

        // check the combinator
        switch ( combinator )
        {
        case ROOT:
            if ( lastLevel > 0 )
            {
                return false;
            }
            break;
        case CHILD:
            if ( ( level - lastLevel ) != 1 )
            {
                return false;
            }
            break;
        case ADJACENT:
            if ( lastLevel != level || ( sequence - lastSequence ) != 1 )
            {
                return false;
            }
            break;
        case SIBLING:
            if ( lastLevel != level || ( sequence - lastSequence ) <= 0 )
            {
                return false;
            }
            break;
        case DESCENDENT:
            if ( (level - lastLevel ) <= 0 )
            {
                return false;
            }
            break;
        default:
            break;
        }
 
        // special check for comment
        if ( tagSelector.equalsIgnoreCase( "comment" ) )
        {
            if ( it.hasNext() )
            {
                token = it.next();
                if ( token.type == HtmlToken.Type.START_COMMENT )
                {
                    // save the level / sequence at which match occurred
                    levelsMatched.add( level, sequence );
                    return true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }

        // special check for cdata
        if ( tagSelector.equalsIgnoreCase( "cdata" ) )
        {
            if ( it.hasNext() )
            {
                token = it.next();
                if ( token.type == HtmlToken.Type.START_CDATA )
                {
                    // save the level / sequence at which match occurred
                    levelsMatched.add( level, sequence );
                    return true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }

        // special check for doctype
        if ( tagSelector.equalsIgnoreCase( "processing-instruction" ) )
        {
            if ( it.hasNext() )
            {
                token = it.next();
                if ( token.type == HtmlToken.Type.PROCESSING_INSTRUCTION )
                {
                    // save the level / sequence at which match occurred
                    levelsMatched.add( level, sequence );
                    return true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }

        // special check for doctype
        if ( tagSelector.equalsIgnoreCase( "doctype" ) )
        {
            if ( it.hasNext() )
            {
                token = it.next();
                if ( token.type == HtmlToken.Type.DOCTYPE )
                {
                    // save the level / sequence at which match occurred
                    levelsMatched.add( level, sequence );
                    return true;
                }
                else
                {
                    return false;
                }
            }
            else
            {
                return false;
            }
        }

        // check the TAG 
        state = false;
        while ( it.hasNext() )
        {
            token = it.next();
            if ( token.type == HtmlToken.Type.TAG_NAME )
            {
                if ( tagSelector.equals( "*" ) || tagSelector.equalsIgnoreCase( token.str ) )
                {
                    state = true;
                }
                break;
            }
            else if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                break;
            }
        }

        if ( ! state )
        {
            return false;
        }

        // check pseudo selector :nth-child
        if ( nthChildA == 0 )
        {
            if ( sequence != nthChildB )
            {
                return false;
            }
        }
        else if ( ( sequence - nthChildB ) * nthChildA < 0
                || ( sequence - nthChildB ) % nthChildA != 0 )
        {
            return false;
        }

        it = tokenQueue.iterator();

        // check the any attribute selectors
        Iterator<AttributeSelector> attributeIterator = attributes.iterator();
        AttributeSelector attributeSelector;
        String attributeName;
        String attributeValue;
        String arr[];
        boolean attributeFound = false;

        while ( attributeIterator.hasNext() )
        {
            attributeSelector = attributeIterator.next();
            it = tokenQueue.iterator();
            attributeFound = false;
            while ( it.hasNext() )
            {
                token = it.next();
                if ( token.type == HtmlToken.Type.ATTRIBUTE_NAME )
                {
                    attributeName = attributeSelector.getAttributeName();
                    if ( attributeName.equalsIgnoreCase( token.str ) )
                    {
                        attributeFound = true;
                        if ( attributeSelector.getComparatorType() == AttributeSelector.ComparatorType.NONE )
                        {
                            // attribute exists so move to next attribute selector (if exists)
                            break;
                        }

                        // advance to the equals
                        while ( it.hasNext() )
                        {
                            token = it.next();
                            if ( token.type == HtmlToken.Type.WHITESPACE )
                            {
                                continue;
                            }
                            else if ( token.type == HtmlToken.Type.EQUALS )
                            {
                                break;
                            }
                            else
                            {
                                state = false;
                                break;
                            }
                        }

                        if ( ! state )
                        {
                            return false;
                        }

                        // advance to the attribute value
                        while ( it.hasNext() )
                        {
                            token = it.next();
                            if ( token.type == HtmlToken.Type.WHITESPACE )
                            {
                                continue;
                            }
                            else if ( token.type == HtmlToken.Type.ATTRIBUTE_VALUE )
                            {
                                break;
                            }
                            else
                            {
                                state = false;
                                break;
                            }
                        }

                        if ( ! state )
                        {
                            return false;
                        }

                        attributeValue = attributeSelector.getAttributeValue();
                        switch ( attributeSelector.getComparatorType() )
                        {
                        case EQUALS:
                            state = token.str.equals( attributeValue );
                            break;
                        case STARTS_WITH:
                            state = token.str.startsWith( attributeValue );
                            break;
                        case ENDS_WITH:
                            state = token.str.endsWith( attributeValue );
                            break;
                        case SUBSTRING:
                            state = ( token.str.indexOf( attributeValue ) >= 0 );
                            break;
                        case CONTAINS:
                            if ( token.str.charAt(0) == '"' || token.str.charAt(0) == '\'' )
                            {
                                arr = token.str.substring(1,token.str.length()-1).split( " +" );
                            }
                            else
                            {
                                arr = token.str.split( " +" );
                            }
                            state = false;
                            for ( String val : arr )
                            {
                                if ( val.equals( attributeValue ) )
                                {
                                    state = true;
                                    break;
                                }
                            }
                            break;
                        case CONTAINS_HYPHENATED:
                            if ( token.str.charAt(0) == '"' || token.str.charAt(0) == '\'' )
                            {
                                arr = token.str.substring(1,token.str.length()-1).split( "-+" );
                            }
                            else
                            {
                                arr = token.str.split( "-+" );
                            }
                            state = false;
                            for ( String val : arr )
                            {
                                if ( val.equals( attributeValue ) )
                                {
                                    state = true;
                                    break;
                                }
                            }
                            break;
                        default:
                            state = false;
                            break;
                        }

                        // attribute found, break out of while loop
                        break;
                    }
                }
            }

            if ( ! state || ! attributeFound )
            {
                return false;
            }
        }

        // save the level / sequence at which match occurred
        levelsMatched.add( level, sequence );

        return true;
    }

    public boolean hasLevelsMatched()
    {
        return levelsMatched.hasMatches();
    }

    public void clearLevelMatched( int level, boolean implied )
    {
        if ( implied )
        {
            levelsMatched.remove( level, -1 );
        }
        else
        {
            levelsMatched.remove( level, 0 );
        }
    }

    public void resetLevelIndex()
    {
        levelsMatched.resetIndex();
    }

    public int getNextMatchedLevel()
    {
        return levelsMatched.getNext();
    }

    public String printMatchedLevels()
    {
        return levelsMatched.toString();
    }

    public void reset()
    {
        levelsMatched.clear();
    }

    public int getLevelAdjustment()
    {
        switch ( combinator )
        {
        case ROOT: return 0;
        case DESCENDENT: return 0;
        case CHILD: return 0;
        case ADJACENT: return 1;
        case SIBLING: return 1;
        default: return 0;
        }
    }

    public String toString()
    {
        StringBuffer sb = new StringBuffer( );
        switch ( combinator )
        {
        case ROOT: break;
        case DESCENDENT: sb.append( " " ); break;
        case CHILD: sb.append( " > " ); break;
        case ADJACENT: sb.append( " + " ); break;
        case SIBLING: sb.append( " ~ " ); break;
        default: sb.append( " ?? " ); break;
        }
        sb.append( tagSelector );
 
        // attribute selectors
        for ( AttributeSelector attribute : attributes )
        {
            sb.append( attribute );
        }

        // pseudo selectors
        if ( nthChildA != 1 || nthChildB != 0 )
        {
            if ( nthChildA == 0 &&nthChildB == 1 )
            {
                sb.append( ":first-child" );
            }
            else
            {
                sb.append( ":nth-child(" );
                if ( nthChildA == 2 && nthChildB == 0 )
                {
                    sb.append( "even" );
                }
                else if ( nthChildA == 2 && nthChildB == 1 )
                {
                    sb.append( "odd" );
                }
                else
                {
                    if ( nthChildA != 0 )
                    {
                        sb.append( nthChildA );
                        sb.append( "n" );
                        if ( nthChildB >= 0 ) sb.append( "+" );
                    }
                    sb.append( nthChildB );
                }
                sb.append( ")" );
            }
        }

        return sb.toString();
    }
}
