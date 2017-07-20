package org.jsoupstream;

import java.util.ArrayDeque;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
   Represents a token returned from the lexer
 */
public class HtmlToken
{
    private static final boolean USE_POOL = true;
    private static final int POOL_BLOCK = 20;

    // The HtmlToken type (simplified for streaming parser)
    public static enum Type
    {
        DOCTYPE,
        PROCESSING_INSTRUCTION,
        OPEN_TAG,
        TAG_NAME,
        ATTRIBUTE_VALUE,
        ATTRIBUTE_NAME,
        CLOSE_TAG,
        END_SELF_CLOSING_TAG,
        OPEN_END_TAG,
        START_COMMENT,
        COMMENT,
        END_COMMENT,
        DATA,
        EQUALS,
        TEXT,
        START_CDATA,
        CDATA,
        END_CDATA,
        WHITESPACE,
        EOF,
        UNKNOWN
    }

    public Type type;
    public SymbolTable.Symbol symbol;
    public String str;
    public boolean onStack = false;

    // create a pool so we can reuse HtmlTokens
    private static ArrayDeque<HtmlToken> pool = new ArrayDeque<HtmlToken>();
    private static ArrayDeque<HtmlToken> unrelinquishedPool = new ArrayDeque<HtmlToken>();
    private static int totalTokens = 0;
    private static Charset charset = StandardCharsets.UTF_8;

    private HtmlToken()
    {
    }

    public static int getPoolSize()
    {
        return pool.size();
    }

    public static void setCharset(Charset charset)
    {
        HtmlToken.charset = charset;
    }

    public static HtmlToken getToken(byte[] s, Type t)
    {
        int len = s.length;
        return getToken( s, 0, len, t, charset );
    }

    public static HtmlToken getToken(byte[] s, int offset, int len, Type t, Charset charset)
    {
        HtmlToken token;

        if ( USE_POOL )
        {
            if ( pool.size() == 0 )
            {
                // add POOL_BLOCK more tokens in the pool
                for (int i = 0; i < POOL_BLOCK; i++)
                {
                    pool.push(new HtmlToken());
                    totalTokens++;
                }
            }
    
            token = pool.pop();
        }
        else
        {
            token = new HtmlToken();
        }

        token.str = (s == null) ? "" : new String( s, offset, len, charset );
        token.type = t;
        if (t == Type.TAG_NAME)
        {
            SymbolTable.Symbol sym = SymbolTable.lookup( token.str );
            token.symbol = sym;
        }
        else
        {
            token.symbol = null;
        }

        token.onStack = false;
        return token;
    }

    public static void relinquish(HtmlToken token)
    {
        if ( USE_POOL )
        {
            if ( ! token.onStack )
            {
                pool.push(token);
            }
            else
            {
                unrelinquishedPool.push( token );
            }
        }
    }

    public static void forceRelinquish(HtmlToken token)
    {
        if ( USE_POOL )
        {
            if ( unrelinquishedPool.remove( token ) )
            {
                pool.push( token );
            }
        }
    }

    public static void replenish()
    {
        if ( USE_POOL )
        {
            HtmlToken token;
            while ( unrelinquishedPool.size() > 0 )
            {
                token = unrelinquishedPool.pop();
                pool.push( token );
            }
        }
    }

    public SymbolTable.Type getSymbolType()
    {
        if ( symbol == null )
        {
            return SymbolTable.Type.UNKNOWN_ELEMENT;
        }
        else
        {
            return symbol.type;
        }
    }

    public String toString()
    {
        return str;
    }
}
