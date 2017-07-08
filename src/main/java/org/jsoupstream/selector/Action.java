package org.jsoupstream.selector;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.StringBuffer;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.OutputStream;
import org.jsoupstream.HtmlToken;

/**
   Represents a single action (function call) with arguments that will get called when the associated selector matches.
 */
public class Action
{
    private final Class<?> cls;
    private final String function;
    private ArrayList<String> arguments = new ArrayList<String>();

    public Action(String func) throws ClassNotFoundException
    {
        int pos = func.lastIndexOf( '.' );
        if ( pos < 0 )
        {
            this.cls = Class.forName( "org.jsoupstream.Functions" );
            this.function = func;
        }
        else
        {
            this.cls = Class.forName( func.substring(0, pos ) );
            this.function = func.substring( pos + 1 );
        }
    }

    public String getFunction()
    {
        return function;
    }

    public int getArgumentCount()
    {
        return arguments.size();
    }

    public void addArgument(String argument)
    {
        arguments.add( argument );
    }

    public String getArgument(int index)
    {
        if ( index < 0 || index >= arguments.size() )
        {
            return null;
        }
        else
        {
            return arguments.get(index);
        }
    }

    public List<String> getArguments()
    {
        return arguments;
    }

    public boolean execute( List<HtmlToken>token_list )
    {
        boolean ret = false;
        try
        {
            // passed String parameters
            Class<?>[] params = new Class<?>[arguments.size() + 1];
            Object[] args = new Object[arguments.size() + 1];
 
            // Token list is sent as first parameter, then remaining are Strings
            params[0] = List.class;
            args[0] = token_list;
            for ( int i = 1; i <= arguments.size(); i++ )
            {
                params[i] = String.class;
                args[i] = arguments.get(i-1);
            }

            Object obj = cls.newInstance();

            // call the method
            Method method = cls.getDeclaredMethod( function, params );
            ret = (boolean)method.invoke(obj, args);
        }
        catch ( Exception e )
        {
            System.out.println( "Error executing "+function+": "+e.getMessage() );
        }
   
        return ret;
    }
        
        
    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append( this.function );
        sb.append( "(" );
        if ( arguments != null )
        {
            Iterator<String> it = arguments.iterator();
            while( it.hasNext() )
            {
                sb.append( it.next() );
                if( it.hasNext() )
                {
                    sb.append(", ");
                }
            }
        }
        sb.append( ")" );
        return sb.toString();
    }
}
