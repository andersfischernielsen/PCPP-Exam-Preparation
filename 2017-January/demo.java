for (int i = 0; i < P; i++) {
    final int from = perTask * i;
    final int to = (i + 1 == P) ? points.length : perTask * (i + 1);
    tasks.add(assignPoints(to, from, points, clusters));