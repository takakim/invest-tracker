import { createTheme, alpha } from '@mui/material/styles';

export const createAppTheme = (mode: 'light' | 'dark' = 'light') => {
  const isDark = mode === 'dark';

  return createTheme({
    palette: {
      mode,
      primary: {
        main: '#2563EB', // Blue 600
        light: '#60A5FA',
        dark: '#1D4ED8',
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: '#0D9488', // Teal 600
        light: '#2DD4BF',
        dark: '#0F766E',
        contrastText: '#FFFFFF',
      },
      background: {
        default: isDark ? '#0B0F19' : '#F8FAFC',
        paper: isDark ? '#111827' : '#FFFFFF',
      },
      text: {
        primary: isDark ? '#F1F5F9' : '#0F172A',
        secondary: isDark ? '#94A3B8' : '#64748B',
      },
      divider: isDark ? alpha('#94A3B8', 0.12) : alpha('#0F172A', 0.08),
      success: {
        main: '#16A34A',
        light: '#4ADE80',
        dark: '#15803D',
      },
      error: {
        main: '#DC2626',
        light: '#F87171',
        dark: '#B91C1C',
      },
      warning: {
        main: '#D97706',
        light: '#FBBF24',
        dark: '#B45309',
      },
      info: {
        main: '#0284C7',
        light: '#38BDF8',
        dark: '#0369A1',
      },
    },
    typography: {
      fontFamily: [
        'Inter',
        '-apple-system',
        'BlinkMacSystemFont',
        '"Segoe UI"',
        'Roboto',
        'sans-serif',
      ].join(','),
      h1: { fontWeight: 700, letterSpacing: '-0.02em' },
      h2: { fontWeight: 700, letterSpacing: '-0.02em' },
      h3: { fontWeight: 600, letterSpacing: '-0.01em' },
      h4: { fontWeight: 600, letterSpacing: '-0.01em' },
      h5: { fontWeight: 600 },
      h6: { fontWeight: 600 },
      subtitle1: { fontWeight: 500 },
      subtitle2: { fontWeight: 500 },
      body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
      body2: { fontSize: '0.875rem', lineHeight: 1.5 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    shape: {
      borderRadius: 10,
    },
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            boxShadow: 'none',
            '&:hover': {
              boxShadow: 'none',
            },
          },
          contained: {
            boxShadow: 'none',
          },
        },
      },
      MuiCssBaseline: {
        styleOverrides: {
          body: {
            scrollbarColor: isDark ? '#334155 #0B0F19' : '#CBD5E1 #F8FAFC',
            '&::-webkit-scrollbar, & *::-webkit-scrollbar': {
              width: 8,
              height: 8,
            },
            '&::-webkit-scrollbar-thumb, & *::-webkit-scrollbar-thumb': {
              borderRadius: 8,
              backgroundColor: isDark ? '#334155' : '#CBD5E1',
              minHeight: 24,
              border: `2px solid ${isDark ? '#0B0F19' : '#F8FAFC'}`,
            },
            '&::-webkit-scrollbar-thumb:hover, & *::-webkit-scrollbar-thumb:hover': {
              backgroundColor: isDark ? '#475569' : '#94A3B8',
            },
          },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: isDark ? '#1E293B' : '#0F172A',
            color: '#FFFFFF',
            fontSize: '0.75rem',
            fontWeight: 500,
            borderRadius: 6,
            padding: '6px 10px',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.2)',
          },
          arrow: {
            color: isDark ? '#1E293B' : '#0F172A',
          },
        },
      },
      MuiPaper: {
        defaultProps: {
          elevation: 0,
        },
        styleOverrides: {
          root: {
            border: `1px solid ${isDark ? alpha('#94A3B8', 0.12) : alpha('#0F172A', 0.08)}`,
            backgroundImage: 'none',
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            transition: 'box-shadow 0.2s ease, transform 0.2s ease',
            '&:hover': {
              boxShadow: isDark
                ? '0 10px 25px -5px rgba(0, 0, 0, 0.4)'
                : '0 10px 25px -5px rgba(15, 23, 42, 0.08)',
            },
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          head: {
            fontWeight: 600,
            color: isDark ? '#94A3B8' : '#475569',
            backgroundColor: isDark ? '#1E293B' : '#F1F5F9',
            letterSpacing: '0.01em',
          },
          body: {
            borderColor: isDark ? alpha('#94A3B8', 0.08) : alpha('#0F172A', 0.06),
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            fontWeight: 500,
            borderRadius: 6,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: 16,
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
          },
        },
      },
    },
  });
};

export const theme = createAppTheme('light');
