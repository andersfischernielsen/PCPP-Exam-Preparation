-module(helloworld). 
-export([start/0,sorter/2,echo/0]).

add(N,L) -> 
    lists:sort(lists:merge(L, [N])).

sorter(L, Out) ->
    receive
        {init, Pid} -> sorter(L, Pid); 
        {num, N} when length(L) < 4 -> sorter(add(N, L), Out);
        {num, N} -> 
            [Smallest|Rest] = add(N, L), 
            Out ! {num, Smallest}, 
            sorter(Rest, Out)
    end.

echo() ->
    receive {num, N} ->
        io:write(N),
        echo() 
    end.

transmit([], _) -> done;
transmit([N|L], Pid) ->
    Pid ! {num, N},
    transmit(L, Pid).

start() ->
    First = spawn(helloworld, sorter, [[], none]), 
    Second = spawn(helloworld, sorter, [[], none]), 
    Echo = spawn(helloworld, echo, []),
    First ! {init, Second},
    Second ! {init, Echo}, 
    transmit([4,7,2,8,6,1,5,3], First), % input 
    transmit([9,9,9,9,9,9,9,9], First). % washout