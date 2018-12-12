-module(helloworld).
-export([start/0, dispatcher/2, worker/1, collector/0]).

dispatcher(Odd, Even) ->
  receive
    {init, O, E, C} ->
      O ! {init, C}, % forward init message to odd actor.
      E ! {init, C}, % forward init message to even actor.
      dispatcher(O, E); % save odd & even actor references.
    {num, N} ->
      case N rem 2 of
        0 -> Even ! {num, N};
        1 -> Odd  ! {num, N}
      end,
      dispatcher(Odd, Even)
  end.

worker(Collector) ->
  receive
    {init, C} ->
      worker(C);
    {num, N} ->
      Res = N*N, % square computation
      Collector ! {num, Res},
      worker(Collector)
  end.

collector() ->
  receive
    {num, N} ->
      io:write(N), % print result received
      io:fwrite("\n"),
      collector()
  end.

start() ->
  % -- SPAWN PHASE ----------
  Dispatcher = spawn(helloworld, dispatcher, [uninit, uninit]),
  Odd = spawn(helloworld, worker, [uninit]),
  Even = spawn(helloworld, worker, [uninit]),
  Collector = spawn(helloworld, collector, []),
      
  % -- INIT PHASE ----------
  Dispatcher ! {init, Odd, Even, Collector},
   
  % -- COMPUTE PHASE ----------
  Dispatcher ! {num, 1},
  Dispatcher ! {num, 2},
  Dispatcher ! {num, 3},
  Dispatcher ! {num, 4},
  Dispatcher ! {num, 5},
  Dispatcher ! {num, 6},
  Dispatcher ! {num, 7},
  Dispatcher ! {num, 8},
  Dispatcher ! {num, 9},
  Dispatcher ! {num, 10}.
